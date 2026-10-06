package ng.proptech.repository;

import java.util.List;
import java.util.UUID;
import ng.proptech.domain.EstateNeighborhood;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstateNeighborhoodRepository extends JpaRepository<EstateNeighborhood, UUID> {
    List<EstateNeighborhood> findBySettlementIdOrderByNameAsc(UUID settlementId);

    boolean existsByIdAndSettlementId(UUID id, UUID settlementId);
}
