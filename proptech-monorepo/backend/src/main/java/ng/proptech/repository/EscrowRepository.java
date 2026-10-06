package ng.proptech.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import ng.proptech.domain.EscrowStatus;
import ng.proptech.domain.EscrowTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscrowRepository extends JpaRepository<EscrowTransaction, UUID> {

    /**
     * SELECT ... FOR UPDATE. Payment confirmation and fund release both lock the row first, so two concurrent
     * requests (double-click, retry storm, malicious replay) can never both move money for one escrow.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EscrowTransaction e where e.id = :id")
    Optional<EscrowTransaction> findByIdForUpdate(@Param("id") UUID id);

    Optional<EscrowTransaction> findFirstByTenantIdAndListingIdAndStatus(UUID tenantId, UUID listingId, EscrowStatus status);
}
