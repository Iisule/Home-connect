package ng.proptech.service;

import java.math.BigDecimal;
import java.util.UUID;
import ng.proptech.domain.AppUser;
import ng.proptech.exception.PaymentGatewayException;
import org.springframework.stereotype.Service;

/**
 * Stands in for a real payment-gateway integration (e.g. Paystack/Flutterwave transfers API). Never called
 * for the platform's own fee or the field officer's cut - those move as internal wallet credits - only the
 * final net-to-agent amount ever needs to leave the platform via an external bank transfer.
 */
@Service
public class MockPaymentGatewayService {

    /** Simulated bank transfer. Throws PaymentGatewayException (which rolls back the whole release) on failure. */
    public String payoutToBank(AppUser agent, BigDecimal amount) {
        if (!agent.hasBankDetails()) {
            throw new PaymentGatewayException(
                    "Agent " + agent.getId() + " has no bank account on file; payout cannot be initiated.");
        }
        if (amount.signum() <= 0) {
            throw new PaymentGatewayException("Payout amount must be positive.");
        }
        // A real integration would call out over HTTPS here and handle async webhook confirmation.
        // The prototype returns a deterministic-looking mock reference so the flow is fully exercisable offline.
        return "MOCKPAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
