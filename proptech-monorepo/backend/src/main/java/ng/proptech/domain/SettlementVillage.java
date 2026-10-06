package ng.proptech.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tier 3: a settlement, ward or village inside an LGA. */
@Entity
@Table(name = "settlement_villages")
@Getter @Setter @NoArgsConstructor
public class SettlementVillage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lga_id", nullable = false)
    private Lga lga;

    @Column(nullable = false, length = 120)
    private String name;
}
