package com.example.healthcare.controller;

import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.notification.ReminderService;
import com.example.healthcare.service.AdminService;
import com.example.healthcare.service.AppointmentService;
import com.example.healthcare.service.AuditService;
import com.example.healthcare.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final AuditService auditService;
    private final ReminderService reminderService;

    public AdminController(AdminService adminService, DoctorService doctorService, AppointmentService appointmentService,
                           AuditService auditService, ReminderService reminderService) {
        this.adminService = adminService;
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
        this.auditService = auditService;
        this.reminderService = reminderService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminService.users();
    }

    @PostMapping("/doctors")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorResponse createDoctor(@Valid @RequestBody AdminCreateDoctorRequest request) {
        return doctorService.createDoctor(request);
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createAdmin(@Valid @RequestBody AdminCreateAdminRequest request) {
        return doctorService.createAdmin(request);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments() {
        return appointmentService.allAppointments();
    }

    @GetMapping("/reports")
    public ReportResponse reports() {
        return adminService.reports();
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(@RequestParam(required = false) AuditAction action,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return auditService.list(action, page, size);
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> notifications(@RequestParam(required = false) NotificationStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "50") int size) {
        return reminderService.list(status, page, size);
    }

    @PostMapping("/notifications/{notificationId}/retry")
    public NotificationResponse retryNotification(@PathVariable Long notificationId) {
        return reminderService.retry(notificationId);
    }
}
