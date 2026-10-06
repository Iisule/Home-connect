package ng.proptech.web;

import java.util.List;
import java.util.UUID;
import ng.proptech.dto.GeoDtos.EstateSummary;
import ng.proptech.dto.GeoDtos.LgaSummary;
import ng.proptech.dto.GeoDtos.SettlementSummary;
import ng.proptech.dto.GeoDtos.StateSummary;
import ng.proptech.repository.EstateNeighborhoodRepository;
import ng.proptech.repository.LgaRepository;
import ng.proptech.repository.SettlementVillageRepository;
import ng.proptech.repository.StateRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Public read-only lookups that power the Tenant Discovery Dashboard's progressive
 * State -> LGA -> Settlement -> Estate dropdowns (SecurityConfig permits GET /api/geo/**).
 */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final StateRepository states;
    private final LgaRepository lgas;
    private final SettlementVillageRepository settlements;
    private final EstateNeighborhoodRepository estates;

    public GeoController(StateRepository states, LgaRepository lgas, SettlementVillageRepository settlements,
                          EstateNeighborhoodRepository estates) {
        this.states = states;
        this.lgas = lgas;
        this.settlements = settlements;
        this.estates = estates;
    }

    @GetMapping("/states")
    public List<StateSummary> states() {
        return states.findAllByOrderByNameAsc().stream()
                .map(s -> new StateSummary(s.getId().toString(), s.getName(), s.getCode()))
                .toList();
    }

    @GetMapping("/states/{stateId}/lgas")
    public List<LgaSummary> lgas(@PathVariable UUID stateId) {
        return lgas.findByStateIdOrderByNameAsc(stateId).stream()
                .map(l -> new LgaSummary(l.getId().toString(), l.getName(), l.isUrban()))
                .toList();
    }

    @GetMapping("/lgas/{lgaId}/settlements")
    public List<SettlementSummary> settlements(@PathVariable UUID lgaId) {
        return settlements.findByLgaIdOrderByNameAsc(lgaId).stream()
                .map(s -> new SettlementSummary(s.getId().toString(), s.getName()))
                .toList();
    }

    @GetMapping("/settlements/{settlementId}/estates")
    public List<EstateSummary> estates(@PathVariable UUID settlementId) {
        return estates.findBySettlementIdOrderByNameAsc(settlementId).stream()
                .map(e -> new EstateSummary(e.getId().toString(), e.getName(), e.isGated()))
                .toList();
    }
}
