package ng.proptech.web;

import jakarta.validation.Valid;
import ng.proptech.domain.AppUser;
import ng.proptech.dto.KycDtos.KycResult;
import ng.proptech.dto.KycDtos.SubmitNinRequest;
import ng.proptech.exception.ApiException;
import ng.proptech.repository.AppUserRepository;
import ng.proptech.security.AuthenticatedUser;
import ng.proptech.service.KycService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Locked down (SecurityConfig: "/api/verification/**" requires a bearer token). */
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final KycService kycService;
    private final AppUserRepository users;

    public VerificationController(KycService kycService, AppUserRepository users) {
        this.kycService = kycService;
        this.users = users;
    }

    @PostMapping(value = "/nin", consumes = "multipart/form-data")
    public KycResult submitNin(@Valid @RequestPart("metadata") SubmitNinRequest req,
                                @RequestPart(value = "ninSlip", required = false) MultipartFile ninSlip,
                                @AuthenticationPrincipal AuthenticatedUser principal) {
        AppUser agent = users.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found."));
        return kycService.submit(agent, req, ninSlip);
    }

    @GetMapping("/nin/status")
    public KycResult status(@AuthenticationPrincipal AuthenticatedUser principal) {
        AppUser agent = users.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found."));
        return kycService.statusFor(agent);
    }
}
