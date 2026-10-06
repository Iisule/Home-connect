package ng.proptech.ai;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ng.proptech.domain.PropertyStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pgvector "embedding" column is deliberately not JPA-mapped (see Property.java javadoc), so every
 * read/write of it happens here with native SQL. This keeps the "<=>" cosine-distance operator visible in
 * plain SQL rather than hidden behind a Hibernate custom-type, which is easier to audit and to EXPLAIN.
 */
@Repository
public class VectorMatchRepository {

    @PersistenceContext
    private EntityManager em;

    /** Result of a duplicate scan: the candidate's id and its cosine distance from the query vector. */
    public record DuplicateCandidate(UUID propertyId, double distance) {}

    /**
     * Writes the AI-service-derived phash + 512-dim embedding onto a property row.
     * Native SQL because the column type ("vector") has no JPA attribute converter registered.
     */
    @Transactional
    public void saveEmbedding(UUID propertyId, String phash, float[] embedding) {
        em.createNativeQuery("""
                UPDATE properties
                SET phash = :phash, embedding = CAST(:vec AS vector)
                WHERE id = :id
                """)
                .setParameter("phash", phash)
                .setParameter("vec", toVectorLiteral(embedding))
                .setParameter("id", propertyId)
                .executeUpdate();
    }

    /**
     * Finds the closest other property in the same LGA by cosine distance, excluding this property itself
     * and anything already resolved (MERGED / REJECTED / PENDING_MERGE, so a chain of near-duplicates does
     * not keep re-flagging against listings that are already being untangled).
     *
     * This exact query shape was validated end-to-end against real AI-service-generated vectors during
     * development: a dimmed/recompressed copy of the same room scored ~0.0002 (correctly flagged at the
     * default 0.15 threshold) while a genuinely different room scored ~0.91 (correctly not flagged).
     */
    @SuppressWarnings("unchecked")
    public Optional<DuplicateCandidate> findClosestInLga(UUID lgaId, UUID excludePropertyId, float[] embedding) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, (embedding <=> CAST(:vec AS vector)) AS distance
                FROM properties
                WHERE lga_id = :lgaId
                  AND id != :excludeId
                  AND embedding IS NOT NULL
                  AND status NOT IN (:excludedStatuses)
                ORDER BY embedding <=> CAST(:vec AS vector)
                LIMIT 1
                """)
                .setParameter("vec", toVectorLiteral(embedding))
                .setParameter("lgaId", lgaId)
                .setParameter("excludeId", excludePropertyId)
                .setParameter("excludedStatuses", List.of(
                        PropertyStatus.MERGED.name(), PropertyStatus.REJECTED.name(), PropertyStatus.PENDING_MERGE.name()))
                .getResultList();

        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.get(0);
        UUID id = (UUID) row[0];
        double distance = ((Number) row[1]).doubleValue();
        return Optional.of(new DuplicateCandidate(id, distance));
    }

    /** pgvector's text input format is "[0.1,0.2,...]" - a plain bracketed, comma-separated float list. */
    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 9 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
