package ng.proptech.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tier 4: an estate or named neighbourhood within a settlement. */
@Entity
@Table(name = "estate_neighborhoods")
@Getter @Setter @NoArgsConstructor
public class EstateNeighborhood {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private SettlementVillage settlement;

    @Column(nullable = false, length = 140)
    private String name;

    /** Gated estates have controlled access, which matters for key hand-over logistics. Column: is_gated. */
    @Column(name = "is_gated", nullable = false)
    private boolean gated;
}
