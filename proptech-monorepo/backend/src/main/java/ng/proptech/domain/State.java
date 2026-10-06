package ng.proptech.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tier 1 of the geo-hierarchy: State > LGA > Settlement/Village > Estate/Neighbourhood > Property. */
@Entity
@Table(name = "states")
@Getter @Setter @NoArgsConstructor
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 4)
    private String code;
}
