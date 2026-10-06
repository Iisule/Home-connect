package ng.proptech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Virtual wallet: the corporate revenue account, or a field officer's earnings balance. */
@Entity
@Table(name = "wallets")
@Getter @Setter @NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private WalletOwnerType ownerType;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static Wallet forUser(UUID userId) {
        Wallet w = new Wallet();
        w.setOwnerType(WalletOwnerType.USER);
        w.setOwnerUserId(userId);
        return w;
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
