package ng.proptech.repository;

import java.util.UUID;
import ng.proptech.domain.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Search uses Specifications so optional filters never send untyped NULL parameters to PostgreSQL. */
public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {
}
