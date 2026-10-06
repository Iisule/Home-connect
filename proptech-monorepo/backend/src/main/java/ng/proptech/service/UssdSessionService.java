package ng.proptech.service;

import java.math.BigDecimal;
import java.util.List;
import ng.proptech.domain.*;
import ng.proptech.dto.KycDtos.KycResult;
import ng.proptech.repository.*;
import ng.proptech.util.Msisdn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the USSD "list a property from a basic phone" flow: State -> LGA -> Village -> Price -> Type.
 *
 * Africa's Talking (and most Nigerian USSD aggregators) call the webhook STATELESSLY: every request carries
 * the FULL input history so far in "text", star-separated (e.g. "1*3*2*250000*1"). There is no server-side
 * session to manage - each step is simply "how many stars are in text so far", and we re-resolve the menu
 * from scratch every time. This is why the service takes no session store: sessionId is accepted (as the
 * gateway contract requires it) but only used for logging/traceability.
 *
 * Every response is either "CON &lt;text&gt;" (show text, keep the session open, wait for more input) or
 * "END &lt;text&gt;" (show text, terminate the session) - the exact contract Africa's Talking expects back.
 */
@Service
public class UssdSessionService {

    private static final String HAUSA_KYC_MISMATCH =
            "Kuskure: Wannan lambar wayar ba ta dace da sunan dake kan NIN dinka ba.";
    private static final String HAUSA_NO_KYC_PROFILE =
            "Kuskure: Ba a sami cikakken bayanin ka na NIN ba. Da fatan za a kammala rajista a app din mu tukuna.";
    private static final String HAUSA_INVALID_INPUT = "Zabi ba daidai ba ne. Da fatan za a sake gwadawa.";
    private static final String HAUSA_INVALID_PRICE = "Kudin haya ba daidai ba ne. Shigar da lamba kawai, misali 250000.";

    private final StateRepository states;
    private final LgaRepository lgas;
    private final SettlementVillageRepository settlements;
    private final AppUserRepository users;
    private final KycProfileRepository kycProfiles;
    private final PropertyRepository properties;
    private final AgentListingRepository listings;
    private final KycService kycService;

    public UssdSessionService(StateRepository states, LgaRepository lgas, SettlementVillageRepository settlements,
                               AppUserRepository users, KycProfileRepository kycProfiles,
                               PropertyRepository properties, AgentListingRepository listings, KycService kycService) {
        this.states = states;
        this.lgas = lgas;
        this.settlements = settlements;
        this.users = users;
        this.kycProfiles = kycProfiles;
        this.properties = properties;
        this.listings = listings;
        this.kycService = kycService;
    }

    @Transactional
    public String handle(String sessionId, String phoneNumberRaw, String text) {
        String msisdn = Msisdn.normalize(phoneNumberRaw);
        String[] parts = (text == null || text.isBlank()) ? new String[0] : text.split("\\*");

        // Step 0: every session opens with the KYC gate. An agent who fails it is stopped before they can
        // ever reach the listing flow, in either English or Hausa depending on their stored preference.
        KycResult kyc = gate(msisdn);
        if (kyc == null) return "END " + HAUSA_NO_KYC_PROFILE;
        if (!kyc.verified()) return "END " + HAUSA_KYC_MISMATCH;

        try {
            if (parts.length == 0) return promptStates();
            if (parts.length == 1) return promptLgas(index(parts[0], states.findAllByOrderByNameAsc().size()));
            if (parts.length == 2) return promptVillages(stateAt(parts[0]), index(parts[1], lgaCount(parts[0])));
            if (parts.length == 3) return promptPrice();
            if (parts.length == 4) return promptType(parts[3]);
            if (parts.length == 5) return finalizeListing(msisdn, parts);
        } catch (IllegalArgumentException badInput) {
            return "END " + HAUSA_INVALID_INPUT;
        }
        return "END " + HAUSA_INVALID_INPUT;
    }

    // --- KYC gate -----------------------------------------------------------------------------------------

    private KycResult gate(String msisdn) {
        var profile = kycProfiles.findByMsisdn(msisdn);
        if (profile.isEmpty()) return null; // triggers HAUSA_NO_KYC_PROFILE
        return kycService.matchAgainstSimRegistry(msisdn, profile.get().getNinFullName());
    }

    // --- Menu steps -----------------------------------------------------------------------------------------

    private String promptStates() {
        List<State> all = states.findAllByOrderByNameAsc();
        StringBuilder sb = new StringBuilder("CON Zabi Jiha (State):\n");
        for (int i = 0; i < all.size(); i++) sb.append(i + 1).append(") ").append(all.get(i).getName()).append('\n');
        return sb.toString().stripTrailing();
    }

    private String promptLgas(int stateIndex) {
        State state = states.findAllByOrderByNameAsc().get(stateIndex);
        List<Lga> all = lgas.findByStateIdOrderByNameAsc(state.getId());
        StringBuilder sb = new StringBuilder("CON Zabi Kananan Hukumomi (LGA) a ").append(state.getName()).append(":\n");
        for (int i = 0; i < all.size(); i++) sb.append(i + 1).append(") ").append(all.get(i).getName()).append('\n');
        return sb.toString().stripTrailing();
    }

    private String promptVillages(State state, int lgaIndex) {
        List<Lga> lgaList = lgas.findByStateIdOrderByNameAsc(state.getId());
        Lga lga = lgaList.get(lgaIndex);
        List<SettlementVillage> all = settlements.findByLgaIdOrderByNameAsc(lga.getId());
        StringBuilder sb = new StringBuilder("CON Zabi Kauye/Unguwa (Village) a ").append(lga.getName()).append(":\n");
        for (int i = 0; i < all.size(); i++) sb.append(i + 1).append(") ").append(all.get(i).getName()).append('\n');
        return sb.toString().stripTrailing();
    }

    private String promptPrice() {
        return "CON Shigar da Kudin Haya na Shekara (Annual Rent), lamba kawai:";
    }

    private String promptType(String priceInput) {
        if (!priceInput.matches("\\d+")) return "END " + HAUSA_INVALID_PRICE;
        StringBuilder sb = new StringBuilder("CON Zabi Nau'in Gida (Property Type):\n");
        PropertyType[] types = PropertyType.values();
        for (int i = 0; i < types.length; i++) sb.append(i + 1).append(") ").append(humanize(types[i])).append('\n');
        return sb.toString().stripTrailing();
    }

    // --- Final step: create the property + the agent's own listing on it ------------------------------------

    private String finalizeListing(String msisdn, String[] parts) {
        AppUser agent = users.findByPhoneNumber(msisdn).orElse(null);
        if (agent == null || agent.getRole() != Role.AGENT) {
            return "END Wannan lambar ba ta yin rajista a matsayin wakili (agent) ba tukuna.";
        }
        State state = states.findAllByOrderByNameAsc().get(index(parts[0], states.findAllByOrderByNameAsc().size()));
        List<Lga> lgaList = lgas.findByStateIdOrderByNameAsc(state.getId());
        Lga lga = lgaList.get(index(parts[1], lgaList.size()));
        List<SettlementVillage> villageList = settlements.findByLgaIdOrderByNameAsc(lga.getId());
        SettlementVillage village = villageList.get(index(parts[2], villageList.size()));

        if (!parts[3].matches("\\d+")) return "END " + HAUSA_INVALID_PRICE;
        BigDecimal annualRent = new BigDecimal(parts[3]);

        PropertyType[] types = PropertyType.values();
        PropertyType type = types[index(parts[4], types.length)];

        Property property = new Property();
        property.setLga(lga);
        property.setSettlement(village);
        property.setTitle(humanize(type) + " a " + village.getName());
        property.setPropertyType(type);
        // No free-text entry on a basic phone for this field; a field officer fills in the precise
        // landmark description during the mandatory physical audit visit before the listing goes VERIFIED.
        property.setLandmarkDescription("Za a cika cikakken bayanin wurin lokacin da jami'in fili ya ziyarta (USSD listing).");
        property.setCreatedBy(agent);
        property.setStatus(PropertyStatus.PENDING_AUDIT);
        properties.save(property);

        AgentListing listing = new AgentListing();
        listing.setProperty(property);
        listing.setAgent(agent);
        listing.setAnnualRent(annualRent);
        listing.setStatus(ListingStatus.PENDING_AUDIT);
        listings.save(listing);

        return "END An samu bayanan gidan ka! Wakilin fili zai tuntube ka domin duba gidan kafin a wallafa shi.\n"
                + "(Property submitted for field audit. Ref: " + property.getId().toString().substring(0, 8) + ")";
    }

    // --- helpers -----------------------------------------------------------------------------------------

    private State stateAt(String indexStr) {
        return states.findAllByOrderByNameAsc().get(index(indexStr, states.findAllByOrderByNameAsc().size()));
    }

    private int lgaCount(String stateIndexStr) {
        State s = stateAt(stateIndexStr);
        return lgas.findByStateIdOrderByNameAsc(s.getId()).size();
    }

    /** Converts a 1-based menu selection into a validated 0-based list index. */
    private int index(String raw, int size) {
        int i;
        try {
            i = Integer.parseInt(raw.trim()) - 1;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a number: " + raw);
        }
        if (i < 0 || i >= size) throw new IllegalArgumentException("Out of range: " + raw);
        return i;
    }

    private String humanize(PropertyType type) {
        return switch (type) {
            case SELF_CONTAIN -> "Self Contain";
            case ROOM_AND_PARLOUR -> "Room & Parlour";
            case TWO_BEDROOM -> "2 Bedroom";
            case THREE_BEDROOM -> "3 Bedroom";
            case DUPLEX -> "Duplex";
            case SHOP -> "Shop";
        };
    }
}
