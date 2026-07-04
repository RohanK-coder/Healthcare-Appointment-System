package com.example.healthcare.controller;

import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.service.AppointmentService;
import com.example.healthcare.service.DoctorService;
import com.example.healthcare.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientController {
    private final DoctorService doctorService;
    private final SlotService slotService;
    private final AppointmentService appointmentService;

    public PatientController(DoctorService doctorService, SlotService slotService, AppointmentService appointmentService) {
        this.doctorService = doctorService;
        this.slotService = slotService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/doctors")
    public List<DoctorResponse> doctors() {
        return doctorService.listDoctors();
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public List<SlotResponse> doctorSlots(@PathVariable Long doctorId,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(defaultValue = "false") boolean availableOnly) {
        if (availableOnly) return slotService.listAvailableDoctorSlots(doctorId);
        return slotService.listDoctorSlots(doctorId, date);
    }

    @PostMapping("/appointments")
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request) {
        return appointmentService.book(request);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> myAppointments() {
        return appointmentService.myPatientAppointments();
    }

    @PutMapping("/appointments/{appointmentId}/cancel")
    public AppointmentResponse cancel(@PathVariable Long appointmentId) {
        return appointmentService.cancelPatientAppointment(appointmentId);
    }
}
