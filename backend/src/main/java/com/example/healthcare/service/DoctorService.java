package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.AdminCreateDoctorRequest;
import com.example.healthcare.dto.DomainDtos.DoctorResponse;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.Doctor;
import com.example.healthcare.enums.Role;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.AppUserRepository;
import com.example.healthcare.repository.DoctorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DoctorService(DoctorRepository doctorRepository, AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctors() {
        return doctorRepository.findByUserActiveTrueOrderByUserFullNameAsc().stream()
                .map(DtoMapper::doctor)
                .toList();
    }

    @Transactional
    public DoctorResponse createDoctor(AdminCreateDoctorRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        AppUser user = new AppUser();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.DOCTOR);
        user = userRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization(request.specialization().trim());
        doctor.setDepartment(request.department().trim());
        doctor.setBio(request.bio());
        return DtoMapper.doctor(doctorRepository.save(doctor));
    }
}
