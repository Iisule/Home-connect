package ng.proptech.security;

import java.util.UUID;
import ng.proptech.domain.Role;

/** The principal placed in the SecurityContext after a valid JWT. Inject it with @AuthenticationPrincipal. */
public record AuthenticatedUser(UUID userId, String email, Role role) {
}
