package com.example.healthcare.dto;

import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.Role;
import com.example.healthcare.enums.SlotStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

public final class DomainDtos {
    private DomainDtos() {}

    public record DoctorResponse(
            Long id,
            Long userId,
            String fullName,
            String email,
            String specialization,
            String department,
            String bio
    ) {}

    public record SlotResponse(
            Long id,
            Long doctorId,
            String doctorName,
            LocalDateTime slotStart,
            LocalDateTime slotEnd,
            SlotStatus status
    ) {}

    public record AppointmentResponse(
            Long id,
            Long patientId,
            String patientName,
            Long doctorId,
            String doctorName,
            Long slotId,
            LocalDateTime slotStart,
            LocalDateTime slotEnd,
            AppointmentStatus status,
            String reason,
            boolean reminderSent,
            SummaryResponse summary
    ) {}

    public record SummaryResponse(
            Long id,
            String doctorNotes,
            String diagnosis,
            String prescription,
            LocalDateTime updatedAt
    ) {}

    public record CreateSlotsRequest(
            @NotNull @FutureOrPresent LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Min(5) int durationMinutes
    ) {}

    public record BookAppointmentRequest(@NotNull Long slotId, String reason) {}

    public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status) {}

    public record CreateSummaryRequest(String doctorNotes, String diagnosis, String prescription) {}

    public record AdminCreateDoctorRequest(
            @NotBlank String fullName,
            @NotBlank String email,
            @NotBlank String password,
            @NotBlank String specialization,
            @NotBlank String department,
            String bio
    ) {}

    public record AdminUserResponse(Long id, String fullName, String email, Role role, boolean active) {}

    public record ReportResponse(long users, long doctors, long patients, long appointments,
                                 long booked, long completed, long canceled, Map<String, Long> byRole) {}
}
