package ng.proptech.repository;

import java.util.Optional;
import ng.proptech.domain.SimRegistryMock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimRegistryMockRepository extends JpaRepository<SimRegistryMock, String> {
    Optional<SimRegistryMock> findByMsisdn(String msisdn);
}
