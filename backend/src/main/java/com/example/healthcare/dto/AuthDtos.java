package com.example.healthcare.dto;

import com.example.healthcare.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6) String password,
            Role role,
            String phone,
            LocalDate dateOfBirth,
            String specialization,
            String department
    ) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, UserResponse user) {}

    public record UserResponse(Long id, String fullName, String email, Role role) {}
}
