package ng.proptech.repository;

import java.util.List;
import java.util.UUID;
import ng.proptech.domain.Lga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LgaRepository extends JpaRepository<Lga, UUID> {
    List<Lga> findByStateIdOrderByNameAsc(UUID stateId);
}
