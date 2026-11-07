package com.example.healthcare.service;

import com.example.healthcare.config.AppProperties;
import com.example.healthcare.dto.AuthDtos.*;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.Patient;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.Role;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.AppUserRepository;
import com.example.healthcare.repository.PatientRepository;
import com.example.healthcare.security.JwtService;
import com.example.healthcare.seed.DataSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final AppUserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final AppProperties.Security security;
    private final boolean demoAccountsProtected;
    // Compared against when the email is unknown, so response time does not reveal which emails exist.
    private final String dummyHash;

    public AuthService(AppUserRepository userRepository,
                       PatientRepository patientRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       CurrentUserService currentUserService,
                       AppProperties properties,
                       @Value("${app.seed.demo-accounts:false}") boolean demoAccountsProtected) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.security = properties.security();
        this.demoAccountsProtected = demoAccountsProtected;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered.");
        }

        AppUser user = new AppUser();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.PATIENT);
        user = userRepository.save(user);

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setPhone(blankToNull(request.phone()));
        patient.setDateOfBirth(request.dateOfBirth());
        patientRepository.save(patient);

        auditService.successFor(user, AuditAction.USER_REGISTERED, "USER", user.getId(), "role=PATIENT");
        return new AuthResponse(jwtService.generateToken(user), DtoMapper.user(user));
    }

    /**
     * Authenticates with per-account lockout: after maxFailedLogins consecutive failures the
     * account is locked for lockoutDuration. Errors are generic so callers cannot probe which
     * emails exist (except while locked, which already implies a known account).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmail(email).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditService.failure(null, email, AuditAction.LOGIN_FAILED, "unknown email");
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditService.failure(user, email, AuditAction.LOGIN_LOCKED, "attempt while locked");
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked after repeated failed logins. Try again later.");
        }
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            String details = user.isActive() ? "bad password, consecutive failures=" + failures : "inactive account";
            if (failures >= security.maxFailedLogins()) {
                user.setLockedUntil(now.plus(security.lockoutDuration()));
                user.setFailedLoginAttempts(0);
                details += ", locked for " + security.lockoutDuration().toMinutes() + "m";
            }
            userRepository.save(user);
            auditService.failure(user, email, AuditAction.LOGIN_FAILED, details);
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            // Transparently rehash older/weaker hashes at the current BCrypt cost.
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userRepository.save(user);
        auditService.successFor(user, AuditAction.LOGIN_SUCCESS, "USER", user.getId(), null);
        return new AuthResponse(jwtService.generateToken(user), DtoMapper.user(user));
    }

    /** Changing the password bumps the token version, which revokes every previously issued JWT. */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        AppUser user = currentUserService.currentUser();
        if (demoAccountsProtected && DataSeeder.DEMO_EMAILS.contains(user.getEmail())) {
            throw ApiException.forbidden("Shared demo accounts cannot change their password. Register your own account to try this.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.failure(user, user.getEmail(), AuditAction.PASSWORD_CHANGED, "wrong current password");
            throw ApiException.badRequest("Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must differ from the current password.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        auditService.successFor(user, AuditAction.PASSWORD_CHANGED, "USER", user.getId(), null);
        return new AuthResponse(jwtService.generateToken(user), DtoMapper.user(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
