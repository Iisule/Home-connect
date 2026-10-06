package ng.proptech.repository;

import java.util.List;
import java.util.UUID;
import ng.proptech.domain.State;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<State, UUID> {
    List<State> findAllByOrderByNameAsc();
}
