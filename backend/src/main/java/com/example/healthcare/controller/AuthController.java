package com.example.healthcare.controller;

import com.example.healthcare.dto.AuthDtos.*;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.service.AuthService;
import com.example.healthcare.service.CurrentUserService;
import com.example.healthcare.service.DtoMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        AppUser user = currentUserService.currentUser();
        return DtoMapper.user(user);
    }
}
