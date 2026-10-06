package ng.proptech.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import ng.proptech.config.AppProperties;
import ng.proptech.domain.*;
import ng.proptech.dto.EscrowDtos.*;
import ng.proptech.exception.ApiException;
import ng.proptech.exception.InvalidOtpException;
import ng.proptech.repository.*;
import ng.proptech.util.Crypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The financial core of the platform: escrow checkout, OTP-gated key-handover release, and the automated
 * three-way fee split (platform / field-officer logistics / agent net) executed atomically at release time.
 */
@Service
public class EscrowService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EscrowRepository escrows;
    private final AgentListingRepository listings;
    private final WalletRepository wallets;
    private final LedgerEntryRepository ledger;
    private final AppUserRepository users;
    private final MockPaymentGatewayService paymentGateway;
    private final AppProperties props;

    public EscrowService(EscrowRepository escrows, AgentListingRepository listings, WalletRepository wallets,
                          LedgerEntryRepository ledger, AppUserRepository users,
                          MockPaymentGatewayService paymentGateway, AppProperties props) {
        this.escrows = escrows;
        this.listings = listings;
        this.wallets = wallets;
        this.ledger = ledger;
        this.users = users;
        this.paymentGateway = paymentGateway;
        this.props = props;
    }

    @Transactional
    public CheckoutResponse checkout(AppUser tenant, CheckoutRequest req) {
        AgentListing listing = listings.findById(req.listingId())
                .orElseThrow(() -> ApiException.notFound("Listing not found."));
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw ApiException.badRequest("This listing is not currently available for checkout.");
        }
        Property property = listing.getProperty();
        AppUser fieldOfficer = property.getAuditedBy() != null ? property.getAuditedBy() : anyFieldOfficer();

        BigDecimal annualRent = listing.getAnnualRent();
        BigDecimal agencyFee = listing.getAgencyFee();
        BigDecimal total = annualRent.add(agencyFee);

        // Fees are computed and FROZEN here - a later change to platform.fees will never alter this escrow.
        BigDecimal platformFee = total.multiply(props.fees().platformPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal logisticsFee = props.fees().logisticsFlat();
        BigDecimal netToAgent = total.subtract(platformFee).subtract(logisticsFee);
        if (netToAgent.signum() < 0) {
            throw ApiException.unprocessable("Fees exceed the total amount for this listing; check pricing.");
        }

        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setListing(listing);
        escrow.setTenant(tenant);
        escrow.setAgent(listing.getAgent());
        escrow.setFieldOfficer(fieldOfficer);
        escrow.setAnnualRent(annualRent);
        escrow.setAgencyFee(agencyFee);
        escrow.setTotalAmount(total);
        escrow.setPlatformFee(platformFee);
        escrow.setLogisticsFee(logisticsFee);
        escrow.setNetToAgent(netToAgent);
        escrow.setStatus(EscrowStatus.PENDING_PAYMENT);
        escrows.save(escrow);

        listing.setStatus(ListingStatus.RESERVED);
        listings.save(listing);

        return new CheckoutResponse(escrow.getId(), annualRent, agencyFee, total, escrow.getStatus());
    }

    /** Simulated payment-gateway confirmation webhook. Mints the 4-digit key-handover OTP once funds land. */
    @Transactional
    public ConfirmPaymentResponse confirmPayment(UUID escrowId, AppUser tenant, ConfirmPaymentRequest req) {
        EscrowTransaction escrow = lockOwned(escrowId, tenant.getId());
        if (escrow.getStatus() != EscrowStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("This escrow is not awaiting payment.");
        }
        String otp = String.format("%04d", RANDOM.nextInt(10_000));
        escrow.setOtpHash(Crypto.hmacSha256Hex(props.escrow().otpPepper(), otp));
        escrow.setOtpAttempts(0);
        escrow.setOtpExpiresAt(Instant.now().plus(props.escrow().otpValidityHours(), ChronoUnit.HOURS));
        escrow.setPaymentReference(req.paymentReference());
        escrow.setStatus(EscrowStatus.FUNDED);
        escrow.setFundedAt(Instant.now());
        escrows.save(escrow);

        // A real deployment SMSes this to the tenant and never returns it over the API; the prototype
        // surfaces it directly in the response so the Checkout screen can display it for the demo.
        return new ConfirmPaymentResponse(escrow.getId(), escrow.getStatus(), otp);
    }

    /**
     * Key-handover release. @Transactional(noRollbackFor) on the wrong-OTP path is essential: the
     * attempt-count increment must survive even though InvalidOtpException propagates, or every retry
     * would be free and the max-attempts lock could never trigger.
     */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public ReleaseResponse release(UUID escrowId, ReleaseRequest req) {
        EscrowTransaction escrow = escrows.findByIdForUpdate(escrowId)
                .orElseThrow(() -> ApiException.notFound("Escrow not found."));

        if (escrow.getStatus() == EscrowStatus.LOCKED) {
            throw ApiException.conflict("This escrow is locked after too many failed codes. Contact support.");
        }
        if (escrow.getStatus() != EscrowStatus.FUNDED) {
            throw ApiException.conflict("This escrow is not ready for key hand-over.");
        }
        if (escrow.getOtpExpiresAt() != null && Instant.now().isAfter(escrow.getOtpExpiresAt())) {
            throw ApiException.conflict("The hand-over code has expired. Ask the tenant to request a new one.");
        }

        String candidateHash = Crypto.hmacSha256Hex(props.escrow().otpPepper(), req.otp());
        if (!candidateHash.equals(escrow.getOtpHash())) {
            escrow.setOtpAttempts(escrow.getOtpAttempts() + 1);
            int remaining = props.escrow().maxOtpAttempts() - escrow.getOtpAttempts();
            if (remaining <= 0) {
                escrow.setStatus(EscrowStatus.LOCKED);
            }
            escrows.save(escrow); // committed even though InvalidOtpException is about to propagate
            throw new InvalidOtpException(Math.max(remaining, 0));
        }

        // --- Correct code: execute the three-way split atomically ------------------------------------------
        Wallet corporate = wallets.lockCorporateWallet()
                .orElseThrow(() -> new IllegalStateException("Corporate wallet is not seeded."));
        corporate.credit(escrow.getPlatformFee());
        wallets.save(corporate);
        ledger.save(LedgerEntry.of(escrow.getId(), corporate.getId(), LedgerEntryType.PLATFORM_FEE,
                escrow.getPlatformFee(), escrow.getPaymentReference(), "3% platform marketplace fee"));

        Wallet officerWallet = wallets.lockByOwnerUserId(escrow.getFieldOfficer().getId())
                .orElseGet(() -> wallets.save(Wallet.forUser(escrow.getFieldOfficer().getId())));
        officerWallet.credit(escrow.getLogisticsFee());
        wallets.save(officerWallet);
        ledger.save(LedgerEntry.of(escrow.getId(), officerWallet.getId(), LedgerEntryType.LOGISTICS_FEE,
                escrow.getLogisticsFee(), escrow.getPaymentReference(), "Flat logistics / audit fee"));

        // Bank payout happens LAST: if the gateway throws, PaymentGatewayException rolls back the whole
        // method, so the wallet credits above never persist without a matching successful agent payout.
        String payoutRef = paymentGateway.payoutToBank(escrow.getAgent(), escrow.getNetToAgent());
        ledger.save(LedgerEntry.of(escrow.getId(), null, LedgerEntryType.AGENT_PAYOUT,
                escrow.getNetToAgent(), payoutRef, "Net annual rent to verified agent bank account"));

        escrow.setStatus(EscrowStatus.RELEASED);
        escrow.setReleasedAt(Instant.now());
        escrow.setPayoutReference(payoutRef);
        escrows.save(escrow);

        closeListing(escrow);

        return new ReleaseResponse(escrow.getId(), escrow.getStatus(), escrow.getPlatformFee(),
                escrow.getLogisticsFee(), escrow.getNetToAgent(), payoutRef);
    }

    private void closeListing(EscrowTransaction escrow) {
        AgentListing listing = escrow.getListing();
        listing.setStatus(ListingStatus.CLOSED);
        listings.save(listing);
    }

    private EscrowTransaction lockOwned(UUID escrowId, UUID tenantId) {
        EscrowTransaction escrow = escrows.findByIdForUpdate(escrowId)
                .orElseThrow(() -> ApiException.notFound("Escrow not found."));
        if (!escrow.getTenant().getId().equals(tenantId)) {
            throw ApiException.forbidden("This escrow does not belong to you.");
        }
        return escrow;
    }

    private AppUser anyFieldOfficer() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.FIELD_OFFICER).findFirst()
                .orElseThrow(() -> ApiException.unprocessable("No field officer is available to audit this hand-over."));
    }
}
