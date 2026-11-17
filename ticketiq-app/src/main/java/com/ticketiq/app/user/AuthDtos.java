package com.ticketiq.app.user;

import com.ticketiq.core.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for sign-in and user management. */
public final class AuthDtos {

    private static final String STRONG_PASSWORD =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])[\\x21-\\x7E]{8,72}$";

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String userId, @NotBlank String password, Role role) {
    }

    public record SignupRequest(
            @Pattern(regexp = "\\d{1,30}", message = "must contain 1 to 30 digits") String employeeId,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "use letters, numbers, dots, underscores or hyphens")
            String userId,
            @NotBlank @Pattern(regexp = STRONG_PASSWORD,
                    message = "must be 8 to 72 printable characters and include lowercase, uppercase, a number and a symbol")
            String password,
            Role role,
            @Size(max = 100) String team) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "use letters, numbers, dots, underscores or hyphens")
            String userId,
            @Pattern(regexp = "\\d{1,30}", message = "must contain 1 to 30 digits") String employeeId,
            @NotBlank @Pattern(regexp = STRONG_PASSWORD,
                    message = "must be 8 to 72 printable characters and include lowercase, uppercase, a number and a symbol")
            String password,
            @NotBlank @Size(max = 100) String fullName,
            @NotNull Role role,
            @Size(max = 100) String team) {
            @Size(max = 100) String team) {
    }

    public record UserResponse(Long id, String userId, String employeeId, String fullName, Role role,
                               String team, boolean enabled, boolean approvalPending) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getUserId(), u.getEmployeeId(), u.getFullName(), u.getRole(),
                    u.getTeam(), u.isEnabled(), u.isApprovalPending());
        }
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Pattern(regexp = STRONG_PASSWORD,
                                                message = "must be 8 to 72 printable characters and include lowercase, uppercase, a number and a symbol")
                                        String newPassword) {
    }

    public record ApprovalRequest(@NotNull Boolean approved) {
    }

    public record ProfileUpdateRequest(@NotBlank @Size(max = 100) String fullName,
                                       @Pattern(regexp = "\\d{1,30}", message = "must contain 1 to 30 digits")
                                       String employeeId) {
    }

    public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                                long expiresInSeconds, UserResponse user) {
    }
}
