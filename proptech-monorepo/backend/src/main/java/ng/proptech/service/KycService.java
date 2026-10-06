package ng.proptech.service;

import ng.proptech.config.AppProperties;
import ng.proptech.domain.AppUser;
import ng.proptech.domain.KycProfile;
import ng.proptech.domain.SimRegistryMock;
import ng.proptech.dto.KycDtos.KycResult;
import ng.proptech.dto.KycDtos.SubmitNinRequest;
import ng.proptech.repository.AppUserRepository;
import ng.proptech.repository.KycProfileRepository;
import ng.proptech.repository.SimRegistryMockRepository;
import ng.proptech.util.Crypto;
import ng.proptech.util.NameMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

/**
 * Simulated Nigerian Identity verification: an agent's declared NIN name is compared against their
 * phone's SIM-registration name (both sourced from the seeded {@code sim_registry_mock} table, standing
 * in for a real telco partner API). This same matching logic backs both the authenticated web endpoint
 * and the USSD KYC loop (see UssdSessionService), so the two channels can never disagree.
 */
@Service
public class KycService {

    private final KycProfileRepository kycProfiles;
    private final SimRegistryMockRepository simRegistry;
    private final AppUserRepository users;
    private final FileStorageService fileStorage;
    private final AppProperties props;

    public KycService(KycProfileRepository kycProfiles, SimRegistryMockRepository simRegistry,
                       AppUserRepository users, FileStorageService fileStorage, AppProperties props) {
        this.kycProfiles = kycProfiles;
        this.simRegistry = simRegistry;
        this.users = users;
        this.fileStorage = fileStorage;
        this.props = props;
    }

    /** Web/app flow: agent uploads a NIN slip photo and declares their NIN + full name. */
    @Transactional
    public KycResult submit(AppUser agent, SubmitNinRequest req, MultipartFile ninSlip) {
        String msisdn = agent.getPhoneNumber();
        String ninHash = Crypto.hmacSha256Hex(props.kyc().ninPepper(), req.nin());
        String last4 = req.nin().substring(req.nin().length() - 4);

        KycProfile profile = kycProfiles.findByMsisdn(msisdn).orElseGet(KycProfile::new);
        profile.setMsisdn(msisdn);
        profile.setNinHash(ninHash);
        profile.setNinLast4(last4);
        profile.setNinFullName(req.ninFullName().trim());
        if (ninSlip != null && !ninSlip.isEmpty()) {
            profile.setSlipKey(fileStorage.storePrivate(ninSlip, "nin-slips"));
        }

        KycResult result = matchAgainstSimRegistry(msisdn, req.ninFullName());
        profile.setStatus(result.verified() ? "VERIFIED" : "REJECTED");
        if (result.verified()) profile.setVerifiedAt(Instant.now());
        kycProfiles.save(profile);

        if (result.verified()) {
            agent.setNinVerified(true);
            users.save(agent);
        }
        return result;
    }

    /** Core comparison, shared by the web endpoint and the USSD channel. */
    public KycResult matchAgainstSimRegistry(String msisdn, String declaredNinName) {
        SimRegistryMock sim = simRegistry.findByMsisdn(msisdn).orElse(null);
        if (sim == null) {
            return new KycResult(false, "REJECTED", "No SIM registration record found for this number.");
        }
        if (!NameMatcher.matches(sim.getRegisteredName(), declaredNinName)) {
            return new KycResult(false, "REJECTED", "SIM registration name does not match the name on the NIN.");
        }
        return new KycResult(true, "VERIFIED", null);
    }

    public KycResult statusFor(AppUser agent) {
        return kycProfiles.findByMsisdn(agent.getPhoneNumber())
                .map(p -> new KycResult("VERIFIED".equals(p.getStatus()), p.getStatus(), null))
                .orElseGet(() -> new KycResult(false, "PENDING", "No NIN submission on file yet."));
    }
}
