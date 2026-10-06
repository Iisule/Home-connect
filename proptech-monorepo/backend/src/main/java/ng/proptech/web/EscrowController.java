package ng.proptech.web;

import jakarta.validation.Valid;
import java.util.UUID;
import ng.proptech.domain.AppUser;
import ng.proptech.dto.EscrowDtos.*;
import ng.proptech.exception.ApiException;
import ng.proptech.repository.AppUserRepository;
import ng.proptech.security.AuthenticatedUser;
import ng.proptech.service.EscrowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Locked down (SecurityConfig: "/api/escrow/**" requires a bearer token). */
@RestController
@RequestMapping("/api/escrow")
public class EscrowController {

    private final EscrowService escrowService;
    private final AppUserRepository users;

    public EscrowController(EscrowService escrowService, AppUserRepository users) {
        this.escrowService = escrowService;
        this.users = users;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('TENANT')")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req, @AuthenticationPrincipal AuthenticatedUser principal) {
        return escrowService.checkout(currentUser(principal), req);
    }

    /** Simulated payment-gateway confirmation; in production this would be a signed server-to-server webhook. */
    @PostMapping("/{escrowId}/confirm-payment")
    @PreAuthorize("hasRole('TENANT')")
    public ConfirmPaymentResponse confirmPayment(@PathVariable UUID escrowId, @Valid @RequestBody ConfirmPaymentRequest req,
                                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return escrowService.confirmPayment(escrowId, currentUser(principal), req);
    }

    /** Either party can trigger release at physical key hand-over, provided they have the correct OTP. */
    @PostMapping("/{escrowId}/release")
    public ReleaseResponse release(@PathVariable UUID escrowId, @Valid @RequestBody ReleaseRequest req) {
        return escrowService.release(escrowId, req);
    }

    private AppUser currentUser(AuthenticatedUser principal) {
        return users.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found."));
    }
}
