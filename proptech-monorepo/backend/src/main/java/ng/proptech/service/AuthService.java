package ng.proptech.service;

import ng.proptech.config.AppProperties;
import ng.proptech.domain.AppUser;
import ng.proptech.domain.Role;
import ng.proptech.dto.AuthDtos.AuthResponse;
import ng.proptech.dto.AuthDtos.LoginRequest;
import ng.proptech.dto.AuthDtos.RegisterRequest;
import ng.proptech.dto.AuthDtos.UserSummary;
import ng.proptech.exception.ApiException;
import ng.proptech.repository.AppUserRepository;
import ng.proptech.security.JwtService;
import ng.proptech.util.Msisdn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwtService, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        Role role;
        try {
            role = Role.valueOf(req.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("role must be one of TENANT, AGENT, FIELD_OFFICER");
        }
        if (role == Role.ADMIN) {
            // Admin accounts are provisioned by hand (seed data / DB), never through public self-registration.
            throw ApiException.forbidden("Cannot self-register as ADMIN.");
        }

        String email = req.email().trim().toLowerCase();
        String phone = Msisdn.normalize(req.phoneNumber());
        if (phone.isEmpty()) throw ApiException.badRequest("phoneNumber is not a valid Nigerian number.");
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with that email already exists.");
        if (users.existsByPhoneNumber(phone)) throw ApiException.conflict("An account with that phone number already exists.");

        AppUser user = new AppUser();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(role);
        users.save(user);

        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email().trim().toLowerCase())
                .orElseThrow(() -> ApiException.badRequest("Invalid email or password."));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("Invalid email or password.");
        }
        return buildResponse(user);
    }

    private AuthResponse buildResponse(AppUser user) {
        String token = jwtService.issue(user);
        UserSummary summary = new UserSummary(user.getId().toString(), user.getFullName(), user.getEmail(),
                user.getPhoneNumber(), user.getRole(), user.isNinVerified(), user.getPreferredLanguage());
        return new AuthResponse(token, "Bearer", props.jwt().expirationMinutes(), summary);
    }
}
