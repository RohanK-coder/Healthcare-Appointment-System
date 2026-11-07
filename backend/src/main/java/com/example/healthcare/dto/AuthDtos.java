package com.example.healthcare.dto;

import com.example.healthcare.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import static com.example.healthcare.dto.Validation.*;

public final class AuthDtos {
    private AuthDtos() {}

    /** Public self-registration always creates a PATIENT; there is deliberately no role field. */
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Past LocalDate dateOfBirth
    ) {}

    public record LoginRequest(@NotBlank @Email @Size(max = 160) String email,
                               @NotBlank @Size(max = 200) String password) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword
    ) {}

    public record AuthResponse(String token, UserResponse user) {}

    public record UserResponse(Long id, String fullName, String email, Role role) {}
}
