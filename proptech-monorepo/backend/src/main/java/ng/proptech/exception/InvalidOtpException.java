package ng.proptech.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the 4-digit key-handover voucher is wrong.
 * EscrowService lists this class in @Transactional(noRollbackFor=...) so the failed-attempt
 * counter is COMMITTED even though an exception propagates - otherwise brute force would be free.
 */
public class InvalidOtpException extends ApiException {

    public InvalidOtpException(int attemptsRemaining) {
        super(HttpStatus.BAD_REQUEST, "INVALID_OTP",
                attemptsRemaining > 0
                        ? "That code is not correct. Attempts remaining: " + attemptsRemaining
                        : "Too many wrong codes. This escrow is locked; contact support.",
                Map.of("attemptsRemaining", attemptsRemaining));
    }
}
