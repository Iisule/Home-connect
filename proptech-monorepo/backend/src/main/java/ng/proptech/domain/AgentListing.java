package ng.proptech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One agent's offer (price + own fee) on a {@link Property}. Many listings per property = co-listing. */
@Entity
@Table(name = "agent_listings", uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "agent_id"}))
@Getter @Setter @NoArgsConstructor
public class AgentListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private AppUser agent;

    @Column(name = "annual_rent", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualRent;

    /** The agent's own commission, paid by the tenant on top of the rent. */
    @Column(name = "agency_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal agencyFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status = ListingStatus.PENDING_AUDIT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BigDecimal totalPayable() {
        return annualRent.add(agencyFee);
    }
}
