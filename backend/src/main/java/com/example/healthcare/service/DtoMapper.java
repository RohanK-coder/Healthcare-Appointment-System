package com.example.healthcare.service;

import com.example.healthcare.dto.AuthDtos.UserResponse;
import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.entity.*;

public final class DtoMapper {
    private DtoMapper() {}

    public static UserResponse user(AppUser user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    public static DoctorResponse doctor(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getUser().getId(),
                doctor.getUser().getFullName(),
                doctor.getUser().getEmail(),
                doctor.getSpecialization(),
                doctor.getDepartment(),
                doctor.getBio()
        );
    }

    public static SlotResponse slot(AppointmentSlot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getDoctor().getId(),
                slot.getDoctor().getUser().getFullName(),
                slot.getSlotStart(),
                slot.getSlotEnd(),
                slot.getStatus()
        );
    }

    public static AppointmentResponse appointment(Appointment appointment, AppointmentSummary summary) {
        SummaryResponse summaryResponse = summary == null ? null : new SummaryResponse(
                summary.getId(), summary.getDoctorNotes(), summary.getDiagnosis(), summary.getPrescription(), summary.getUpdatedAt()
        );
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getUser().getFullName(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getUser().getFullName(),
                appointment.getSlot().getId(),
                appointment.getSlot().getSlotStart(),
                appointment.getSlot().getSlotEnd(),
                appointment.getStatus(),
                appointment.getReason(),
                appointment.isReminderSent(),
                summaryResponse
        );
    }
}
