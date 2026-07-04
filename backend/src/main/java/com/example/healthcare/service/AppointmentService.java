package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.*;
import com.example.healthcare.entity.*;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentSummaryRepository summaryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentSlotRepository slotRepository,
                              AppointmentSummaryRepository summaryRepository,
                              PatientRepository patientRepository,
                              DoctorRepository doctorRepository,
                              CurrentUserService currentUserService) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.summaryRepository = summaryRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request) {
        AppUser current = currentUserService.currentUser();
        Patient patient = patientRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Patient profile not found."));

        AppointmentSlot slot = slotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> ApiException.notFound("Slot not found."));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw ApiException.badRequest("This slot is no longer available.");
        }

        slot.setStatus(SlotStatus.BOOKED);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(slot.getDoctor());
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setReason(request.reason());
        return DtoMapper.appointment(appointmentRepository.save(appointment), null);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myPatientAppointments() {
        AppUser current = currentUserService.currentUser();
        Patient patient = patientRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Patient profile not found."));
        return appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId())
                .stream().map(this::toAppointmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myDoctorAppointments() {
        AppUser current = currentUserService.currentUser();
        Doctor doctor = doctorRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Doctor profile not found."));
        return appointmentRepository.findByDoctorIdOrderByCreatedAtDesc(doctor.getId())
                .stream().map(this::toAppointmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> allAppointments() {
        return appointmentRepository.findAll().stream().map(this::toAppointmentResponse).toList();
    }

    @Transactional
    public AppointmentResponse cancelPatientAppointment(Long appointmentId) {
        AppUser current = currentUserService.currentUser();
        Patient patient = patientRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Patient profile not found."));
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment not found."));
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw ApiException.forbidden("You can only cancel your own appointment.");
        }
        return cancel(appointment);
    }

    @Transactional
    public AppointmentResponse updateDoctorAppointmentStatus(Long appointmentId, UpdateAppointmentStatusRequest request) {
        AppUser current = currentUserService.currentUser();
        Doctor doctor = doctorRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Doctor profile not found."));
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment not found."));
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw ApiException.forbidden("You can only update your own appointments.");
        }
        if (request.status() == AppointmentStatus.CANCELED) {
            return cancel(appointment);
        }
        appointment.setStatus(request.status());
        return toAppointmentResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse addSummary(Long appointmentId, CreateSummaryRequest request) {
        AppUser current = currentUserService.currentUser();
        Doctor doctor = doctorRepository.findByUserId(current.getId())
                .orElseThrow(() -> ApiException.notFound("Doctor profile not found."));
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Appointment not found."));
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw ApiException.forbidden("You can only summarize your own appointments.");
        }
        AppointmentSummary summary = summaryRepository.findByAppointmentId(appointmentId).orElseGet(AppointmentSummary::new);
        summary.setAppointment(appointment);
        summary.setDoctorNotes(request.doctorNotes());
        summary.setDiagnosis(request.diagnosis());
        summary.setPrescription(request.prescription());
        summary = summaryRepository.save(summary);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
        return DtoMapper.appointment(appointment, summary);
    }

    private AppointmentResponse cancel(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw ApiException.badRequest("Completed appointments cannot be canceled.");
        }
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointment.getSlot().setStatus(SlotStatus.AVAILABLE);
        return toAppointmentResponse(appointmentRepository.save(appointment));
    }

    private AppointmentResponse toAppointmentResponse(Appointment appointment) {
        AppointmentSummary summary = summaryRepository.findByAppointmentId(appointment.getId()).orElse(null);
        return DtoMapper.appointment(appointment, summary);
    }
}
