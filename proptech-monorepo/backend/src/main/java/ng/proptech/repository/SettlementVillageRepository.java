package ng.proptech.repository;

import java.util.List;
import java.util.UUID;
import ng.proptech.domain.SettlementVillage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementVillageRepository extends JpaRepository<SettlementVillage, UUID> {
    List<SettlementVillage> findByLgaIdOrderByNameAsc(UUID lgaId);

    /** Integrity guard: a property's settlement must really belong to the LGA the duplicate check is scoped to. */
    boolean existsByIdAndLgaId(UUID id, UUID lgaId);
}
