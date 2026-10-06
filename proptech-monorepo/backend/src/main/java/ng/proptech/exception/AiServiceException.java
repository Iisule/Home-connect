package ng.proptech.exception;

/** The Python AI microservice is unreachable or returned an invalid payload. */
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiServiceException(String message) {
        super(message);
    }
}
