package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.AdminCreateAdminRequest;
import com.example.healthcare.dto.DomainDtos.AdminCreateDoctorRequest;
import com.example.healthcare.dto.DomainDtos.AdminUserResponse;
import com.example.healthcare.dto.DomainDtos.DoctorResponse;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.Doctor;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.Role;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.AppUserRepository;
import com.example.healthcare.repository.DoctorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public DoctorService(DoctorRepository doctorRepository, AppUserRepository userRepository,
                         PasswordEncoder passwordEncoder, AuditService auditService) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctors() {
        return doctorRepository.findByUserActiveTrueOrderByUserFullNameAsc().stream()
                .map(DtoMapper::doctor)
                .toList();
    }

    @Transactional
    public DoctorResponse createDoctor(AdminCreateDoctorRequest request) {
        AppUser user = newUser(request.fullName(), request.email(), request.password(), Role.DOCTOR);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization(request.specialization().trim());
        doctor.setDepartment(request.department().trim());
        doctor.setBio(request.bio() == null || request.bio().isBlank() ? null : request.bio().trim());
        doctor = doctorRepository.save(doctor);
        auditService.success(AuditAction.DOCTOR_CREATED, "USER", user.getId(), "doctorId=" + doctor.getId());
        return DtoMapper.doctor(doctor);
    }

    @Transactional
    public AdminUserResponse createAdmin(AdminCreateAdminRequest request) {
        AppUser user = newUser(request.fullName(), request.email(), request.password(), Role.ADMIN);
        auditService.success(AuditAction.ADMIN_CREATED, "USER", user.getId(), null);
        return DtoMapper.adminUser(user);
    }

    private AppUser newUser(String fullName, String rawEmail, String password, Role role) {
        String email = AuthService.normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered.");
        }
        AppUser user = new AppUser();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }
}
