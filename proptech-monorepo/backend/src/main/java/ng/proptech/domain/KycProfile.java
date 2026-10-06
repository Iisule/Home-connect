package ng.proptech.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One agent's NIN verification record, keyed by their normalised phone number (E.164).
 * The raw National Identification Number is never persisted - only its HMAC and last 4 digits.
 */
@Entity
@Table(name = "kyc_profiles")
@Getter @Setter @NoArgsConstructor
public class KycProfile {

    @Id
    @Column(length = 20)
    private String msisdn;

    @Column(name = "nin_hash", nullable = false, length = 64)
    private String ninHash;

    @Column(name = "nin_last4", nullable = false, length = 4)
    private String ninLast4;

    @Column(name = "nin_full_name", nullable = false, length = 160)
    private String ninFullName;

    @Column(name = "slip_key", length = 120)
    private String slipKey;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING | VERIFIED | REJECTED

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
