package ng.proptech.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tenants, agents, field officers and admins share one table; behaviour is driven by {@link Role}. */
@Entity
@Table(name = "app_users")
@Getter @Setter @NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "nin_verified", nullable = false)
    private boolean ninVerified;

    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = "en";

    @Column(name = "bank_name", length = 80)
    private String bankName;

    @Column(name = "bank_code", length = 6)
    private String bankCode;

    @Column(name = "bank_account_number", length = 10)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", length = 160)
    private String bankAccountName;

    // Agent trust metrics shown in the co-listing comparison grid
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "deals_closed", nullable = false)
    private int dealsClosed;

    @Column(name = "avg_response_minutes", nullable = false)
    private int avgResponseMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean hasBankDetails() {
        return bankCode != null && bankAccountNumber != null && bankAccountName != null;
    }
}
