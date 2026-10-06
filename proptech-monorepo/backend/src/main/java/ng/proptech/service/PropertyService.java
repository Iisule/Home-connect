package ng.proptech.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import ng.proptech.ai.VectorMatchRepository;
import ng.proptech.ai.VectorMatchRepository.DuplicateCandidate;
import ng.proptech.config.AppProperties;
import ng.proptech.domain.*;
import ng.proptech.dto.PropertyDtos.*;
import ng.proptech.exception.ApiException;
import ng.proptech.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Property intake, AI-assisted duplicate detection, and the read models behind the Tenant Discovery
 * Dashboard and the Merged Co-Listing Detail page.
 */
@Service
public class PropertyService {

    private final PropertyRepository properties;
    private final AgentListingRepository listings;
    private final LgaRepository lgas;
    private final SettlementVillageRepository settlements;
    private final EstateNeighborhoodRepository estates;
    private final FileStorageService fileStorage;
    private final AiClientService aiClient;
    private final VectorMatchRepository vectorMatch;
    private final AppProperties props;

    public PropertyService(PropertyRepository properties, AgentListingRepository listings, LgaRepository lgas,
                            SettlementVillageRepository settlements, EstateNeighborhoodRepository estates,
                            FileStorageService fileStorage, AiClientService aiClient,
                            VectorMatchRepository vectorMatch, AppProperties props) {
        this.properties = properties;
        this.listings = listings;
        this.lgas = lgas;
        this.settlements = settlements;
        this.estates = estates;
        this.fileStorage = fileStorage;
        this.aiClient = aiClient;
        this.vectorMatch = vectorMatch;
        this.props = props;
    }

    @Transactional
    public PropertyUploadResponse create(CreatePropertyRequest req, List<MultipartFile> photos, AppUser agent) {
        if (photos == null || photos.isEmpty()) {
            throw ApiException.badRequest("At least one property photo is required.");
        }
        // Geo integrity: settlement must really belong to the LGA, estate (if any) to the settlement.
        if (!settlements.existsByIdAndLgaId(req.settlementId(), req.lgaId())) {
            throw ApiException.badRequest("That settlement does not belong to the selected LGA.");
        }
        if (req.estateId() != null && !estates.existsByIdAndSettlementId(req.estateId(), req.settlementId())) {
            throw ApiException.badRequest("That estate does not belong to the selected settlement.");
        }
        Lga lga = lgas.findById(req.lgaId()).orElseThrow(() -> ApiException.notFound("LGA not found."));
        SettlementVillage settlement = settlements.findById(req.settlementId())
                .orElseThrow(() -> ApiException.notFound("Settlement not found."));
        EstateNeighborhood estate = req.estateId() != null
                ? estates.findById(req.estateId()).orElseThrow(() -> ApiException.notFound("Estate not found."))
                : null;

        Property property = new Property();
        property.setLga(lga);
        property.setSettlement(settlement);
        property.setEstate(estate);
        property.setTitle(req.title().trim());
        property.setPropertyType(req.propertyType());
        property.setLandmarkDescription(req.landmarkDescription().trim());
        if (req.latitude() != null && req.longitude() != null) {
            property.setLocation(GeoPoints.of(req.latitude(), req.longitude()));
        }
        property.setCreatedBy(agent);
        property.setStatus(PropertyStatus.PENDING_AUDIT);

        List<String> photoUrls = new ArrayList<>();
        for (MultipartFile photo : photos) {
            photoUrls.add(fileStorage.storePublic(photo, "properties"));
        }
        property.setPhotoUrls(photoUrls);
        property.setPrimaryImageUrl(photoUrls.get(0));
        properties.save(property);

        // The agent's own offer on the home they just listed.
        AgentListing listing = new AgentListing();
        listing.setProperty(property);
        listing.setAgent(agent);
        listing.setAnnualRent(req.annualRent());
        listing.setAgencyFee(req.agencyFee() != null ? req.agencyFee() : BigDecimal.ZERO);
        listing.setStatus(ListingStatus.PENDING_AUDIT);
        listings.save(listing);

        // AI photo analysis: never let a slow/unavailable AI service silently lose a listing - if it fails,
        // the property still exists (PENDING_AUDIT, un-flagged) and a human auditor can re-run analysis later.
        boolean flagged = false;
        UUID duplicateOfId = null;
        Double distance = null;
        try {
            AiClientService.AiGenerateResult ai = aiClient.generate(property.getPrimaryImageUrl());
            float[] vector = ai.vectorAsFloatArray();
            vectorMatch.saveEmbedding(property.getId(), ai.phash(), vector);

            double threshold = props.ai().duplicateDistanceThreshold();
            var candidate = vectorMatch.findClosestInLga(lga.getId(), property.getId(), vector);
            if (candidate.isPresent() && candidate.get().distance() < threshold) {
                DuplicateCandidate dc = candidate.get();
                flagged = true;
                duplicateOfId = dc.propertyId();
                distance = dc.distance();
                property.setAiFlagged(true);
                property.setDuplicateOfId(duplicateOfId);
                property.setStatus(PropertyStatus.PENDING_MERGE);
                properties.save(property);
            }
        } catch (Exception e) {
            // Swallowed deliberately: photo-analysis failure must never block a listing from being created.
            // GlobalExceptionHandler already logs AiServiceException at the source in AiClientService.
        }

        return new PropertyUploadResponse(property.getId(), flagged, duplicateOfId, distance, property.getPrimaryImageUrl());
    }

    @Transactional
    public void decideMerge(UUID propertyId, AppUser agent, MergeDecisionRequest decision) {
        Property property = properties.findById(propertyId).orElseThrow(() -> ApiException.notFound("Property not found."));
        if (!property.isAiFlagged() || property.getDuplicateOfId() == null) {
            throw ApiException.badRequest("This property was not flagged as a possible duplicate.");
        }
        List<AgentListing> mine = listings.findByPropertyId(propertyId);
        boolean owns = mine.stream().anyMatch(l -> l.getAgent().getId().equals(agent.getId()));
        if (!owns) throw ApiException.forbidden("Only the listing agent can resolve a duplicate flag.");

        if (decision.mergeIntoExisting()) {
            // Move every listing from the new (duplicate) property onto the canonical one, so all agents'
            // offers show up together in the Merged Co-Listing Detail comparison grid.
            Property canonical = properties.findById(property.getDuplicateOfId())
                    .orElseThrow(() -> ApiException.notFound("Original property no longer exists."));
            for (AgentListing l : mine) {
                l.setProperty(canonical);
                l.setStatus(ListingStatus.ACTIVE);
                listings.save(l);
            }
            property.setStatus(PropertyStatus.MERGED);
        } else {
            // Agent insists it is a genuinely different home; clear the flag and let it stand on its own.
            property.setAiFlagged(false);
            property.setDuplicateOfId(null);
            property.setStatus(PropertyStatus.VERIFIED);
            mine.forEach(l -> { l.setStatus(ListingStatus.ACTIVE); listings.save(l); });
        }
        properties.save(property);
    }

    public List<PropertySummary> search(UUID lgaId, UUID settlementId, PropertyType propertyType) {
        Specification<Property> spec = Specification.where(statusIn(PropertyStatus.VERIFIED, PropertyStatus.RESERVED));
        if (lgaId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("lga").get("id"), lgaId));
        if (settlementId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("settlement").get("id"), settlementId));
        if (propertyType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("propertyType"), propertyType));

        return properties.findAll(spec).stream().map(this::toSummary)
                .sorted(Comparator.comparing(PropertySummary::createdAt).reversed())
                .toList();
    }

    public PropertyDetail detail(UUID propertyId) {
        Property property = properties.findById(propertyId).orElseThrow(() -> ApiException.notFound("Property not found."));
        List<AgentListing> active = listings.findByPropertyIdAndStatus(propertyId, ListingStatus.ACTIVE);
        List<AgentOffer> offers = active.stream()
                .sorted(Comparator.comparing(AgentListing::totalPayable))
                .map(l -> new AgentOffer(l.getId(), l.getAgent().getId(), l.getAgent().getFullName(),
                        l.getAnnualRent(), l.getAgencyFee(), l.totalPayable(), l.getAgent().getRating(),
                        l.getAgent().getDealsClosed(), l.getAgent().getAvgResponseMinutes(), l.getStatus()))
                .toList();

        return new PropertyDetail(property.getId(), property.getTitle(), property.getPropertyType(),
                property.getLga().getName(), property.getSettlement().getName(),
                property.getEstate() != null ? property.getEstate().getName() : null,
                property.getLandmarkDescription(), GeoPoints.lat(property.getLocation()), GeoPoints.lng(property.getLocation()),
                property.getPhotoUrls(), property.getStatus(), offers);
    }

    private PropertySummary toSummary(Property p) {
        List<AgentListing> active = listings.findByPropertyIdAndStatus(p.getId(), ListingStatus.ACTIVE);
        BigDecimal lowest = active.stream().map(AgentListing::getAnnualRent).min(BigDecimal::compareTo).orElse(null);
        return new PropertySummary(p.getId(), p.getTitle(), p.getPropertyType(), p.getLga().getName(),
                p.getSettlement().getName(), p.getEstate() != null ? p.getEstate().getName() : null,
                p.getLandmarkDescription(), GeoPoints.lat(p.getLocation()), GeoPoints.lng(p.getLocation()),
                p.getPrimaryImageUrl(), p.getStatus(), p.isAiFlagged(), active.size(), lowest, p.getCreatedAt());
    }

    private static Specification<Property> statusIn(PropertyStatus... statuses) {
        return (root, q, cb) -> root.get("status").in((Object[]) statuses);
    }
}
