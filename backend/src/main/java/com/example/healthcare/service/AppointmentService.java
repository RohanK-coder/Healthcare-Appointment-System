package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.entity.*;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentSummaryRepository summaryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentSlotRepository slotRepository,
                              AppointmentSummaryRepository summaryRepository,
                              PatientRepository patientRepository,
                              DoctorRepository doctorRepository,
                              CurrentUserService currentUserService,
                              AuditService auditService,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.summaryRepository = summaryRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Double-booking protection, in two layers:
     * 1. SELECT ... FOR UPDATE on the slot row serializes concurrent bookers; the losers see BOOKED.
     * 2. A partial unique index on appointments(slot_id) for non-canceled rows rejects anything
     *    that slips past (mapped to 409 by the exception handler).
     */
    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request) {
        AppUser current = currentUserService.currentUser();
        Patient patient = patientRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Patient profile not found."));

        AppointmentSlot slot = slotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> ApiException.notFound("Slot not found."));

        if (!slot.getSlotStart().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.badRequest("This slot is in the past.");
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw ApiException.conflict("This slot is no longer available.");
        }

        slot.setStatus(SlotStatus.BOOKED);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(slot.getDoctor());
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setReason(trimToNull(request.reason()));
        appointment = appointmentRepository.saveAndFlush(appointment);
        auditService.success(AuditAction.APPOINTMENT_BOOKED, "APPOINTMENT", appointment.getId(), "slotId=" + slot.getId());
        return DtoMapper.appointment(appointment, null);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myPatientAppointments() {
        AppUser current = currentUserService.currentUser();
        Patient patient = patientRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Patient profile not found."));
        return withSummaries(appointmentRepository.findDetailedByPatientId(patient.getId()));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myDoctorAppointments() {
        Doctor doctor = currentDoctor();
        return withSummaries(appointmentRepository.findDetailedByDoctorId(doctor.getId()));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> allAppointments() {
        return withSummaries(appointmentRepository.findAllDetailed());
    }

    @Transactional
    public AppointmentResponse cancelPatientAppointment(Long appointmentId) {
        AppUser current = currentUserService.currentUser();
        Patient patient = patientRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Patient profile not found."));
        Appointment appointment = findAppointment(appointmentId);
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw ApiException.forbidden("You can only cancel your own appointment.");
        }
        return cancel(appointment);
    }

    /** Doctors move a BOOKED appointment to COMPLETED, NO_SHOW or CANCELED; other states are final. */
    @Transactional
    public AppointmentResponse updateDoctorAppointmentStatus(Long appointmentId, UpdateAppointmentStatusRequest request) {
        Doctor doctor = currentDoctor();
        Appointment appointment = findAppointment(appointmentId);
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw ApiException.forbidden("You can only update your own appointments.");
        }
        AppointmentStatus target = request.status();
        if (target == AppointmentStatus.BOOKED) {
            throw ApiException.badRequest("Appointments cannot be moved back to BOOKED.");
        }
        if (target == AppointmentStatus.CANCELED) {
            return cancel(appointment);
        }
        requireStatus(appointment, AppointmentStatus.BOOKED, "Only booked appointments can be updated.");
        AppointmentStatus previous = appointment.getStatus();
        appointment.setStatus(target);
        appointmentRepository.save(appointment);
        auditService.success(AuditAction.APPOINTMENT_STATUS_CHANGED, "APPOINTMENT", appointment.getId(),
                previous + "->" + target);
        return toAppointmentResponse(appointment);
    }

    @Transactional
    public AppointmentResponse addSummary(Long appointmentId, CreateSummaryRequest request) {
        Doctor doctor = currentDoctor();
        Appointment appointment = findAppointment(appointmentId);
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw ApiException.forbidden("You can only summarize your own appointments.");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED && appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw ApiException.badRequest("Summaries can only be written for booked or completed appointments.");
        }
        AppointmentSummary summary = summaryRepository.findByAppointmentId(appointmentId).orElseGet(AppointmentSummary::new);
        boolean created = summary.getId() == null;
        summary.setAppointment(appointment);
        summary.setDoctorNotes(trimToNull(request.doctorNotes()));
        summary.setDiagnosis(trimToNull(request.diagnosis()));
        summary.setPrescription(trimToNull(request.prescription()));
        summary = summaryRepository.save(summary);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
        // Only record that a summary changed, never its clinical content.
        auditService.success(AuditAction.SUMMARY_SAVED, "APPOINTMENT", appointment.getId(), created ? "created" : "updated");
        return DtoMapper.appointment(appointment, summary);
    }

    /**
     * Only BOOKED appointments can be canceled. Without this check a second cancel of an already
     * canceled appointment would free a slot that someone else has since rebooked.
     */
    private AppointmentResponse cancel(Appointment appointment) {
        requireStatus(appointment, AppointmentStatus.BOOKED, "Only booked appointments can be canceled.");
        AppointmentSlot slot = slotRepository.findByIdForUpdate(appointment.getSlot().getId())
                .orElseThrow(() -> ApiException.notFound("Slot not found."));
        appointment.setStatus(AppointmentStatus.CANCELED);
        slot.setStatus(SlotStatus.AVAILABLE);
        appointmentRepository.save(appointment);
        auditService.success(AuditAction.APPOINTMENT_CANCELED, "APPOINTMENT", appointment.getId(), "slotId=" + slot.getId());
        return toAppointmentResponse(appointment);
    }

    private Doctor currentDoctor() {
        AppUser current = currentUserService.currentUser();
        return doctorRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Doctor profile not found."));
    }

    /** Loads an appointment for modification; the row lock is held until the transaction ends. */
    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment not found."));
    }

    private static void requireStatus(Appointment appointment, AppointmentStatus expected, String message) {
        if (appointment.getStatus() != expected) throw ApiException.badRequest(message);
    }

    private AppointmentResponse toAppointmentResponse(Appointment appointment) {
        AppointmentSummary summary = summaryRepository.findByAppointmentId(appointment.getId()).orElse(null);
        return DtoMapper.appointment(appointment, summary);
    }

    /** Loads all summaries for a page of appointments in one query instead of one per row. */
    private List<AppointmentResponse> withSummaries(List<Appointment> appointments) {
        if (appointments.isEmpty()) return List.of();
        Map<Long, AppointmentSummary> summaries = summaryRepository
                .findByAppointmentIdIn(appointments.stream().map(Appointment::getId).toList())
                .stream().collect(Collectors.toMap(s -> s.getAppointment().getId(), Function.identity()));
        return appointments.stream().map(a -> DtoMapper.appointment(a, summaries.get(a.getId()))).toList();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
