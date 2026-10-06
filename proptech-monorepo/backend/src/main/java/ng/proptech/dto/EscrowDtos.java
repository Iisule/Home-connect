package ng.proptech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;
import ng.proptech.domain.EscrowStatus;

public final class EscrowDtos {

    private EscrowDtos() {}

    public record CheckoutRequest(@NotNull UUID listingId) {}

    /** Returned right after checkout: the amount to pay, NOT the OTP itself (that only exists after payment). */
    public record CheckoutResponse(UUID escrowId, BigDecimal annualRent, BigDecimal agencyFee,
                                    BigDecimal totalAmount, EscrowStatus status) {}

    /** Simulated payment-gateway webhook / confirm-payment call. Mints the 4-digit key-handover OTP. */
    public record ConfirmPaymentRequest(@NotBlank String paymentReference) {}

    public record ConfirmPaymentResponse(UUID escrowId, EscrowStatus status,
                                          String otpForDemoOnly /* prototype convenience; a real deployment SMSes this to the tenant, never returns it in the API */) {}

    public record ReleaseRequest(@NotBlank @Pattern(regexp = "\\d{4}") String otp) {}

    public record ReleaseResponse(UUID escrowId, EscrowStatus status, BigDecimal platformFee,
                                   BigDecimal logisticsFee, BigDecimal netToAgent, String payoutReference) {}
}
