package ng.proptech.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tier 2: Local Government Area. The AI duplicate check is always scoped to one LGA. */
@Entity
@Table(name = "lgas")
@Getter @Setter @NoArgsConstructor
public class Lga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    @Column(nullable = false, length = 120)
    private String name;

    /** Urban LGAs use street-style addressing; rural ones rely on landmarks. Column: is_urban. */
    @Column(name = "is_urban", nullable = false)
    private boolean urban;
}
