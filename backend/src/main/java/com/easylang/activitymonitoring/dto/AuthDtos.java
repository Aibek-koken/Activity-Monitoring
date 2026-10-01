package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 160, message = "Email must be 160 characters or less")
            String email,
            @NotBlank(message = "Password is required")
            @Size(max = 72, message = "Password must be 72 characters or less")
            String password
    ) {
    }

    public record UserResponse(Long id, String initials, String fullName, String email, Role role) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getInitials(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getRole()
            );
        }
    }

    public record AuthResponse(UserResponse user) {
    }

    public record CsrfResponse(String token, String headerName) {
    }
}
