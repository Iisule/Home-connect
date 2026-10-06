package ng.proptech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Funds held for one rental until the key hand-over. All monetary splits are computed and FROZEN at checkout,
 * so a later fee-policy change can never alter what an in-flight escrow pays out.
 */
@Entity
@Table(name = "escrow_transactions")
@Getter @Setter @NoArgsConstructor
public class EscrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private AgentListing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private AppUser tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private AppUser agent;

    /** The officer who audited the property; receives the logistics fee. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_officer_id", nullable = false)
    private AppUser fieldOfficer;

    @Column(name = "annual_rent", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualRent;

    @Column(name = "agency_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal agencyFee;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "platform_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "logistics_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal logisticsFee;

    @Column(name = "net_to_agent", nullable = false, precision = 14, scale = 2)
    private BigDecimal netToAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscrowStatus status = EscrowStatus.PENDING_PAYMENT;

    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "payment_reference", length = 80)
    private String paymentReference;

    @Column(name = "payout_reference", length = 80)
    private String payoutReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "funded_at")
    private Instant fundedAt;

    @Column(name = "released_at")
    private Instant releasedAt;
}
