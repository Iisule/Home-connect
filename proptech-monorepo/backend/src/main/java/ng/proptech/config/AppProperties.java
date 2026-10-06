package ng.proptech.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed view of every "proptech.*" property in application.properties.
 * Keeping configuration in one immutable record means a typo fails at startup,
 * not at 2 a.m. when the first tenant pays.
 */
@ConfigurationProperties(prefix = "proptech")
public record AppProperties(Jwt jwt, Ai ai, Storage storage, Cors cors, Fees fees, Escrow escrow, Kyc kyc) {

    public record Jwt(String secret, long expirationMinutes) {}

    /** duplicateDistanceThreshold: cosine distance under which two photos are "the same room". */
    public record Ai(String baseUrl, double duplicateDistanceThreshold, int connectTimeoutMs, int readTimeoutMs) {}

    public record Storage(String uploadDir, String publicBaseUrl) {}

    public record Cors(List<String> allowedOrigins) {}

    /** platformPercent = 3.0 means 3 %; logisticsFlat is in naira. */
    public record Fees(BigDecimal platformPercent, BigDecimal logisticsFlat) {}

    public record Escrow(String otpPepper, int maxOtpAttempts, int otpValidityHours) {}

    public record Kyc(String ninPepper) {}
}
