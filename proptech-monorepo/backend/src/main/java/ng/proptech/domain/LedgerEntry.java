package ng.proptech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable audit line: one row per money movement produced by a release. */
@Entity
@Table(name = "ledger_entries")
@Getter @Setter @NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escrow_id", nullable = false)
    private UUID escrowId;

    /** null for bank payouts, because that money leaves the platform. */
    @Column(name = "wallet_id")
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 80)
    private String reference;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static LedgerEntry of(UUID escrowId, UUID walletId, LedgerEntryType type, BigDecimal amount,
                                 String reference, String description) {
        LedgerEntry e = new LedgerEntry();
        e.escrowId = escrowId;
        e.walletId = walletId;
        e.entryType = type;
        e.amount = amount;
        e.reference = reference;
        e.description = description;
        return e;
    }
}
