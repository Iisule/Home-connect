package ng.proptech.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ng.proptech.domain.Role;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email String email,
            @NotBlank String phoneNumber,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String role // TENANT | AGENT | FIELD_OFFICER  (ADMIN cannot self-register)
    ) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, String tokenType, long expiresInMinutes, UserSummary user) {}

    public record UserSummary(String id, String fullName, String email, String phoneNumber, Role role,
                               boolean ninVerified, String preferredLanguage) {}
}
