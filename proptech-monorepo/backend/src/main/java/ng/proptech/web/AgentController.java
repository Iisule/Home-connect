package ng.proptech.web;

import java.math.BigDecimal;
import java.util.List;
import ng.proptech.domain.Wallet;
import ng.proptech.dto.AgentDtos.MyListingSummary;
import ng.proptech.dto.AgentDtos.WalletSummary;
import ng.proptech.repository.AgentListingRepository;
import ng.proptech.repository.WalletRepository;
import ng.proptech.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Locked down (SecurityConfig: "/api/agents/**" requires a bearer token). */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentListingRepository listings;
    private final WalletRepository wallets;

    public AgentController(AgentListingRepository listings, WalletRepository wallets) {
        this.listings = listings;
        this.wallets = wallets;
    }

    @GetMapping("/me/listings")
    @PreAuthorize("hasAnyRole('AGENT')")
    public List<MyListingSummary> myListings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return listings.findByAgentIdOrderByCreatedAtDesc(principal.userId()).stream()
                .map(l -> new MyListingSummary(l.getId(), l.getProperty().getId(), l.getProperty().getTitle(),
                        l.getAnnualRent(), l.getAgencyFee(), l.getStatus()))
                .toList();
    }

    /** Field officers earn the logistics fee, so this also serves their wallet view. */
    @GetMapping("/me/wallet")
    @PreAuthorize("hasAnyRole('AGENT', 'FIELD_OFFICER')")
    public WalletSummary myWallet(@AuthenticationPrincipal AuthenticatedUser principal) {
        BigDecimal balance = wallets.findByOwnerUserId(principal.userId()).map(Wallet::getBalance).orElse(BigDecimal.ZERO);
        return new WalletSummary(balance);
    }
}
