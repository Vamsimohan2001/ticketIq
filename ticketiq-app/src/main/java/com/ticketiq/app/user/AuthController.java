package com.ticketiq.app.user;

import com.ticketiq.app.security.CurrentUser;
import com.ticketiq.app.user.AuthDtos.CreateUserRequest;
import com.ticketiq.app.user.AuthDtos.LoginRequest;
import com.ticketiq.app.user.AuthDtos.LoginResponse;
import com.ticketiq.app.user.AuthDtos.RefreshRequest;
import com.ticketiq.app.user.AuthDtos.SignupRequest;
import com.ticketiq.app.user.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.userId(), request.password());
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return auth.signup(request.employeeId(), request.fullName(), request.userId(), request.password());
    }

    @PostMapping("/auth/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @GetMapping("/auth/me")
    public UserResponse me() {
        return users.findById(CurrentUser.get().id()).map(UserResponse::from).orElseThrow();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return auth.createUser(request.userId(), request.employeeId(), request.password(),
                request.fullName(), request.role());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public List<UserResponse> listUsers() {
        return auth.listUsers();
    }
}
