package ng.proptech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class KycDtos {

    private KycDtos() {}

    /** JSON "metadata" part of the multipart NIN-verification request; the slip image rides alongside. */
    public record SubmitNinRequest(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "NIN must be exactly 11 digits") String nin,
            @NotBlank String ninFullName
    ) {}

    public record KycResult(boolean verified, String status, String reasonIfFailed) {}
}
