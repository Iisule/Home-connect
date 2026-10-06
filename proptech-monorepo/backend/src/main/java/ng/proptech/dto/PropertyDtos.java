package ng.proptech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ng.proptech.domain.ListingStatus;
import ng.proptech.domain.PropertyStatus;
import ng.proptech.domain.PropertyType;

public final class PropertyDtos {

    private PropertyDtos() {}

    /** JSON part ("metadata") of the multipart POST /api/properties request; photos ride alongside as files. */
    public record CreatePropertyRequest(
            @NotNull UUID lgaId,
            @NotNull UUID settlementId,
            UUID estateId,
            @NotBlank String title,
            @NotNull PropertyType propertyType,
            @NotBlank String landmarkDescription,
            Double latitude,
            Double longitude,
            @NotNull BigDecimal annualRent,
            BigDecimal agencyFee
    ) {}

    /** What the client sees right after upload: either "all clear" or a merge decision to make. */
    public record PropertyUploadResponse(UUID propertyId, boolean aiFlagged, UUID possibleDuplicateOfId,
                                          Double duplicateDistance, String primaryImageUrl) {}

    public record PropertySummary(UUID id, String title, PropertyType propertyType, String lgaName,
                                   String settlementName, String estateName, String landmarkDescription,
                                   Double latitude, Double longitude, String primaryImageUrl,
                                   PropertyStatus status, boolean aiFlagged, int competingListingCount,
                                   BigDecimal lowestAnnualRent, Instant createdAt) {}

    public record PropertyDetail(UUID id, String title, PropertyType propertyType, String lgaName,
                                  String settlementName, String estateName, String landmarkDescription,
                                  Double latitude, Double longitude, List<String> photoUrls,
                                  PropertyStatus status, List<AgentOffer> offers) {}

    /** One row in the comparative agent grid on the merged co-listing detail page. */
    public record AgentOffer(UUID listingId, UUID agentId, String agentName, BigDecimal annualRent,
                              BigDecimal agencyFee, BigDecimal totalPayable, BigDecimal agentRating,
                              int dealsClosed, int avgResponseMinutes, ListingStatus status) {}

    /** Agent's decision when their new upload was AI-flagged as a likely duplicate. */
    public record MergeDecisionRequest(boolean mergeIntoExisting) {}
}
