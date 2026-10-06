package ng.proptech.exception;

/** The payment provider refused or failed a call. Deliberately NOT an ApiException: it must roll back the transaction. */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }
}
