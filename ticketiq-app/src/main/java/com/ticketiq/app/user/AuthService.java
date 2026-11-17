package com.ticketiq.app.user;

import com.ticketiq.app.common.ConflictException;
import com.ticketiq.app.common.BadRequestException;
import com.ticketiq.app.common.TooManyRequestsException;
import com.ticketiq.app.common.UnauthorizedException;
import com.ticketiq.app.common.ForbiddenException;
import com.ticketiq.app.common.NotFoundException;
import com.ticketiq.app.config.AppProperties;
import com.ticketiq.app.user.AuthDtos.LoginResponse;
import com.ticketiq.app.user.AuthDtos.UserResponse;
import com.ticketiq.core.ai.RateLimiter;
import com.ticketiq.core.model.Role;
import com.ticketiq.core.security.JwtService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Sign-in, token refresh and user creation. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> AGENT_TEAMS = Set.of("Service Desk", "HR Systems", "Finance Systems",
            "Application Engineering", "Identity and Access");

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
        return login(userId, password, null);
    }

    public LoginResponse login(String userId, String password, Role requestedRole) {
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
        if (!user.isEnabled()) {
            throw new ForbiddenException(user.isApprovalPending()
                    ? "This account is awaiting administrator approval" : "This account is disabled");
        }
        if (requestedRole != null && user.getRole() != requestedRole) {
            throw new UnauthorizedException("The selected role does not match this account");
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

    public UserResponse createUser(String userId, String employeeId, String password, String fullName, Role role,
                                   String team) {
        if (role == Role.AGENT && (team == null || !AGENT_TEAMS.contains(team.trim()))) {
            throw new BadRequestException("Select a valid support team for the agent account");
        }
        if (users.existsByUserIdIgnoreCase(userId.trim())) {
            throw new ConflictException("A user with that user ID already exists");
        }
        if (employeeId != null && users.existsByEmployeeId(employeeId.trim())) {
            throw new ConflictException("A profile with that employee ID already exists");
        }
        User saved = users.save(new User(userId, encoder.encode(password), fullName, role, employeeId, team, true));
        return UserResponse.from(saved);
    }

    public UserResponse signup(String employeeId, String fullName, String userId, String password, Role role,
                               String team) {
        String normalizedUserId = userId.trim().toLowerCase();
        String normalizedEmployeeId = employeeId == null ? null : employeeId.trim();
        Role requestedRole = role == null ? Role.USER : role;
        if (requestedRole == Role.AGENT && (team == null || !AGENT_TEAMS.contains(team.trim()))) {
            throw new BadRequestException("Select a valid support team for the agent account");
        }
        if (users.existsByUserIdIgnoreCase(normalizedUserId)) {
            throw new ConflictException("A user with that user ID already exists");
        }
        if (normalizedEmployeeId != null && users.existsByEmployeeId(normalizedEmployeeId)) {
            throw new ConflictException("A profile with that employee ID already exists");
        }
        User saved = users.save(new User(normalizedUserId, encoder.encode(password), fullName,
                requestedRole, normalizedEmployeeId, team, false));
        return UserResponse.from(saved);
    }

    public UserResponse approveUser(long id, boolean approved) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Account not found"));
        user.setEnabled(approved);
        user.setApprovalPending(false);
        return UserResponse.from(users.save(user));
    }

    public UserResponse updateProfile(long id, String fullName, String employeeId) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Account not found"));
        String normalizedEmployeeId = employeeId == null ? null : employeeId.trim();
        if (normalizedEmployeeId != null && users.existsByEmployeeId(normalizedEmployeeId)
                && !normalizedEmployeeId.equals(user.getEmployeeId())) {
            throw new ConflictException("A profile with that employee ID already exists");
        }
        user.setFullName(fullName);
        user.setEmployeeId(normalizedEmployeeId);
        return UserResponse.from(users.save(user));
    }

    public void changePassword(long id, String currentPassword, String newPassword) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Account not found"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
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
