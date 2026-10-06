package ng.proptech.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import ng.proptech.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /** Row-locked so concurrent releases cannot lose an update to the balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.ownerType = ng.proptech.domain.WalletOwnerType.CORPORATE")
    Optional<Wallet> lockCorporateWallet();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.ownerUserId = :userId")
    Optional<Wallet> lockByOwnerUserId(@Param("userId") UUID userId);

    /** Plain, non-locking read - safe to call outside a write transaction (e.g. a GET wallet-balance view). */
    Optional<Wallet> findByOwnerUserId(UUID ownerUserId);
}
