package com.example.healthcare.dto;

import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.enums.Role;
import com.example.healthcare.enums.SlotStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.example.healthcare.dto.Validation.*;

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
            @Min(5) @Max(240) int durationMinutes
    ) {}

    public record BookAppointmentRequest(@NotNull Long slotId, @Size(max = 500) String reason) {}

    public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status) {}

    public record CreateSummaryRequest(
            @Size(max = 4000) String doctorNotes,
            @Size(max = 1000) String diagnosis,
            @Size(max = 1000) String prescription
    ) {}

    public record AdminCreateDoctorRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 120) String specialization,
            @NotBlank @Size(max = 120) String department,
            @Size(max = 2000) String bio
    ) {}

    public record AdminCreateAdminRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password
    ) {}

    public record AdminUserResponse(Long id, String fullName, String email, Role role, boolean active) {}

    public record ReportResponse(long users, long doctors, long patients, long appointments,
                                 long booked, long completed, long canceled, Map<String, Long> byRole,
                                 Map<String, Long> notificationsByStatus) {}

    public record AuditLogResponse(Long id, LocalDateTime occurredAt, Long actorUserId, String actorEmail,
                                   String actorRole, AuditAction action, String entityType, Long entityId,
                                   String outcome, String ipAddress, String details) {}

    public record NotificationResponse(Long id, Long appointmentId, String recipientEmail, String type,
                                       String message, NotificationStatus status, int attempts,
                                       LocalDateTime nextAttemptAt, LocalDateTime sentAt, String lastError,
                                       LocalDateTime createdAt) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
}
