package ng.proptech.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** One consistent JSON error envelope for the whole API. Internal details are logged, never returned. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(Instant timestamp, int status, String code, String message, Map<String, Object> details) {}

    private static ResponseEntity<ErrorBody> body(HttpStatus s, String code, String msg, Map<String, Object> details) {
        return ResponseEntity.status(s).body(new ErrorBody(Instant.now(), s.value(), code, msg, details));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException e) {
        return body(e.getStatus(), e.getCode(), e.getMessage(), e.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorBody> handleMalformed(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request is missing data or is not valid JSON.", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported content type. For multipart parts, send 'metadata' with Content-Type: application/json.", Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Upload is larger than 10 MB.", Map.of());
    }

    // Method security (@PreAuthorize) throws these from INSIDE the controller layer, so a catch-all
    // Exception handler would swallow them as 500s unless we map them explicitly.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleDenied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do that.", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorBody> handleAuth(AuthenticationException e) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in.", Map.of());
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorBody> handleAi(AiServiceException e) {
        log.error("AI service failure", e);
        return body(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                "Photo analysis is temporarily unavailable. Please try again shortly.", Map.of());
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ErrorBody> handlePayment(PaymentGatewayException e) {
        log.error("Payment gateway failure", e);
        return body(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                "The payment provider could not complete the request. No funds were moved.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side.", Map.of());
    }
}
