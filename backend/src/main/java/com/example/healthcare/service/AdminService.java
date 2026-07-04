package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.AdminUserResponse;
import com.example.healthcare.dto.DomainDtos.ReportResponse;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.Role;
import com.example.healthcare.repository.AppUserRepository;
import com.example.healthcare.repository.AppointmentRepository;
import com.example.healthcare.repository.DoctorRepository;
import com.example.healthcare.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private final AppUserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public AdminService(AppUserRepository userRepository,
                        DoctorRepository doctorRepository,
                        PatientRepository patientRepository,
                        AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users() {
        return userRepository.findAll().stream()
                .map(u -> new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse reports() {
        Map<String, Long> byRole = Arrays.stream(Role.values())
                .collect(Collectors.toMap(Enum::name, role -> (long) userRepository.findByRole(role).size()));
        return new ReportResponse(
                userRepository.count(),
                doctorRepository.count(),
                patientRepository.count(),
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.BOOKED),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELED),
                byRole
        );
    }
}
