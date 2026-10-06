package ng.proptech.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

/**
 * Tier 5: the physical home. Several agents can attach {@link AgentListing}s to the same Property (co-listing).
 *
 * The pgvector "embedding" column is intentionally NOT mapped here: it is read and written with native SQL in
 * ng.proptech.ai.VectorMatchRepository, which avoids depending on a Hibernate vector type and keeps the
 * cosine-distance operator visible in plain SQL.
 */
@Entity
@Table(name = "properties")
@Getter @Setter @NoArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Denormalised on purpose: duplicate detection filters on lga_id without joining through the settlement. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lga_id", nullable = false)
    private Lga lga;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private SettlementVillage settlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id")
    private EstateNeighborhood estate;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 32)
    private PropertyType propertyType;

    /** CRITICAL for offline routing: directions a person on a basic phone can follow ("3rd gate after the red water tank"). */
    @Column(name = "landmark_description", nullable = false, columnDefinition = "text")
    private String landmarkDescription;

    /** Optional GPS pin. JTS Point mapped by Hibernate Spatial; X = longitude, Y = latitude, SRID 4326. */
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "primary_image_url", columnDefinition = "text")
    private String primaryImageUrl;

    @Column(length = 64)
    private String phash;

    /** true => structural duplicate of another home in the same LGA (cosine distance below threshold). */
    @Column(name = "ai_flagged", nullable = false)
    private boolean aiFlagged;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyStatus status = PropertyStatus.PENDING_AUDIT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audited_by")
    private AppUser auditedBy;

    @Column(name = "audited_at")
    private Instant auditedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "property_photos", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "photo_url", nullable = false, columnDefinition = "text")
    private List<String> photoUrls = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
