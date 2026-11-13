package com.ticketiq.app.user;

import com.ticketiq.app.common.ConflictException;
import com.ticketiq.app.common.TooManyRequestsException;
import com.ticketiq.app.common.UnauthorizedException;
import com.ticketiq.app.config.AppProperties;
import com.ticketiq.app.user.AuthDtos.LoginResponse;
import com.ticketiq.app.user.AuthDtos.UserResponse;
import com.ticketiq.core.ai.RateLimiter;
import com.ticketiq.core.model.Role;
import com.ticketiq.core.security.JwtService;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Sign-in, token refresh and user creation. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter failedLogins;
    private final AppProperties props;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RateLimiter loginFailureLimiter, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.failedLogins = loginFailureLimiter;
        this.props = props;
    }

    public LoginResponse login(String userId, String password) {
        String key = userId.trim().toLowerCase();
        if (failedLogins.isBlocked(key)) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Try again in a few minutes.");
        }
        User user = users.findByUserIdIgnoreCase(key).orElse(null);
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            failedLogins.tryAcquire(key);
            log.warn("Failed sign-in for {}", key);
            throw new UnauthorizedException("Wrong user ID or password");
        }
        failedLogins.reset(key);
        return tokensFor(user);
    }

    public LoginResponse refresh(String refreshToken) {
        JwtService.Claims claims;
        try {
            claims = jwt.verify(refreshToken);
        } catch (JwtService.InvalidTokenException e) {
            throw new UnauthorizedException("Your session has expired. Please sign in again.");
        }
        if (!"refresh".equals(claims.type())) {
            throw new UnauthorizedException("That is not a refresh token");
        }
        User user = users.findById(Long.parseLong(claims.subject()))
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        return tokensFor(user);
    }

    public UserResponse createUser(String userId, String employeeId, String password, String fullName, Role role) {
        if (users.existsByUserIdIgnoreCase(userId.trim())) {
            throw new ConflictException("A user with that user ID already exists");
        }
        if (employeeId != null && users.existsByEmployeeId(employeeId.trim())) {
            throw new ConflictException("A profile with that employee ID already exists");
        }
        User saved = users.save(new User(userId, encoder.encode(password), fullName, role, employeeId));
        return UserResponse.from(saved);
    }

    public UserResponse signup(String employeeId, String fullName, String userId, String password) {
        String normalizedUserId = userId.trim().toLowerCase();
        String normalizedEmployeeId = employeeId.trim();
        if (users.existsByUserIdIgnoreCase(normalizedUserId)) {
            throw new ConflictException("A user with that user ID already exists");
        }
        if (users.existsByEmployeeId(normalizedEmployeeId)) {
            throw new ConflictException("A profile with that employee ID already exists");
        }
        User saved = users.save(new User(normalizedUserId, encoder.encode(password), fullName,
                Role.USER, normalizedEmployeeId));
        return UserResponse.from(saved);
    }

    public List<UserResponse> listUsers() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    private LoginResponse tokensFor(User user) {
        Duration access = Duration.ofMinutes(props.jwt().accessMinutes());
        Duration refresh = Duration.ofDays(props.jwt().refreshDays());
        String id = String.valueOf(user.getId());
        return new LoginResponse(
                jwt.issue(id, user.getUserId(), user.getRole().name(), "access", access),
                jwt.issue(id, user.getUserId(), user.getRole().name(), "refresh", refresh),
                "Bearer", access.toSeconds(), UserResponse.from(user));
    }
}
