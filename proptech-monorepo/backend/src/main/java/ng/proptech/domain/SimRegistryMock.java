package ng.proptech.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stands in for the telecom operator's SIM-registration database, which a real integration would query
 * over a partner API. The USSD KYC loop compares this name against the agent's declared NIN name.
 */
@Entity
@Table(name = "sim_registry_mock")
@Getter @Setter @NoArgsConstructor
public class SimRegistryMock {

    @Id
    @Column(length = 20)
    private String msisdn;

    @Column(name = "registered_name", nullable = false, length = 160)
    private String registeredName;
}
