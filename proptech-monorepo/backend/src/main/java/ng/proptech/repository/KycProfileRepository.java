package ng.proptech.repository;

import java.util.Optional;
import ng.proptech.domain.KycProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycProfileRepository extends JpaRepository<KycProfile, String> {
    Optional<KycProfile> findByMsisdn(String msisdn);
}
