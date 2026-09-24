package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,
            @NotBlank(message = "Password is required")
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
