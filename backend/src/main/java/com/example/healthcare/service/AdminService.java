package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.AdminUserResponse;
import com.example.healthcare.dto.DomainDtos.ReportResponse;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.enums.Role;
import com.example.healthcare.repository.AppUserRepository;
import com.example.healthcare.repository.AppointmentRepository;
import com.example.healthcare.repository.DoctorRepository;
import com.example.healthcare.repository.NotificationRepository;
import com.example.healthcare.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final AppUserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    public AdminService(AppUserRepository userRepository,
                        DoctorRepository doctorRepository,
                        PatientRepository patientRepository,
                        AppointmentRepository appointmentRepository,
                        NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream().map(DtoMapper::adminUser).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse reports() {
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role role : Role.values()) byRole.put(role.name(), 0L);
        userRepository.countByRole().forEach(rc -> byRole.put(rc.getRole().name(), rc.getTotal()));

        Map<String, Long> byNotificationStatus = new LinkedHashMap<>();
        for (NotificationStatus status : NotificationStatus.values()) byNotificationStatus.put(status.name(), 0L);
        notificationRepository.countByStatus().forEach(sc -> byNotificationStatus.put(sc.getStatus().name(), sc.getTotal()));

        return new ReportResponse(
                userRepository.count(),
                doctorRepository.count(),
                patientRepository.count(),
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.BOOKED),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELED),
                byRole,
                byNotificationStatus
        );
    }
}
