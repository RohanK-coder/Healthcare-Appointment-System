package com.example.healthcare.controller;

import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.service.AppointmentService;
import com.example.healthcare.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctor")
public class DoctorController {
    private final SlotService slotService;
    private final AppointmentService appointmentService;

    public DoctorController(SlotService slotService, AppointmentService appointmentService) {
        this.slotService = slotService;
        this.appointmentService = appointmentService;
    }

    @PostMapping("/slots")
    public List<SlotResponse> createSlots(@Valid @RequestBody CreateSlotsRequest request) {
        return slotService.createMySlots(request);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments() {
        return appointmentService.myDoctorAppointments();
    }

    @PutMapping("/appointments/{appointmentId}/status")
    public AppointmentResponse updateStatus(@PathVariable Long appointmentId,
                                            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateDoctorAppointmentStatus(appointmentId, request);
    }

    @PostMapping("/appointments/{appointmentId}/summary")
    public AppointmentResponse addSummary(@PathVariable Long appointmentId,
                                          @RequestBody CreateSummaryRequest request) {
        return appointmentService.addSummary(appointmentId, request);
    }
}
