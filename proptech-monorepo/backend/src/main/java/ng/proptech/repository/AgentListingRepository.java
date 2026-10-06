package ng.proptech.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ng.proptech.domain.AgentListing;
import ng.proptech.domain.ListingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentListingRepository extends JpaRepository<AgentListing, UUID> {

    /** Active offers for one property; agents are fetched eagerly because the comparison grid shows their metrics. */
    @EntityGraph(attributePaths = "agent")
    List<AgentListing> findByPropertyIdAndStatus(UUID propertyId, ListingStatus status);

    List<AgentListing> findByPropertyIdInAndStatus(Collection<UUID> propertyIds, ListingStatus status);

    List<AgentListing> findByPropertyId(UUID propertyId);

    boolean existsByPropertyIdAndAgentId(UUID propertyId, UUID agentId);

    @EntityGraph(attributePaths = "property")
    List<AgentListing> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
}
