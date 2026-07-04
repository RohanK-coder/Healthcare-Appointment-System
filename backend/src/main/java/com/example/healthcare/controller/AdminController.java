package com.example.healthcare.controller;

import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.service.AdminService;
import com.example.healthcare.service.AppointmentService;
import com.example.healthcare.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;

    public AdminController(AdminService adminService, DoctorService doctorService, AppointmentService appointmentService) {
        this.adminService = adminService;
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminService.users();
    }

    @PostMapping("/doctors")
    public DoctorResponse createDoctor(@Valid @RequestBody AdminCreateDoctorRequest request) {
        return doctorService.createDoctor(request);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments() {
        return appointmentService.allAppointments();
    }

    @GetMapping("/reports")
    public ReportResponse reports() {
        return adminService.reports();
    }
}
