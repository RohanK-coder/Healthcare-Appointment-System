package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.CreateSlotsRequest;
import com.example.healthcare.dto.DomainDtos.SlotResponse;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.entity.Doctor;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.AppointmentSlotRepository;
import com.example.healthcare.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlotService {
    static final int MAX_DAYS_AHEAD = 180;

    private final AppointmentSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;

    public SlotService(AppointmentSlotRepository slotRepository, DoctorRepository doctorRepository,
                       CurrentUserService currentUserService, AuditService auditService, Clock clock) {
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public List<SlotResponse> createMySlots(CreateSlotsRequest request) {
        AppUser user = currentUserService.currentUser();
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("Doctor profile not found."));

        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("End time must be after start time.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.date().isAfter(now.toLocalDate().plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest("Slots can be created at most " + MAX_DAYS_AHEAD + " days ahead.");
        }

        LocalDate date = request.date();
        LocalDateTime dayEnd = LocalDateTime.of(date, request.endTime());
        // Iterate on LocalDateTime: LocalTime arithmetic wraps at midnight and could loop forever.
        LocalDateTime cursor = LocalDateTime.of(date, request.startTime());
        Set<LocalDateTime> existing = slotRepository
                .findByDoctorIdAndSlotStartBetweenOrderBySlotStartAsc(doctor.getId(), cursor, dayEnd)
                .stream().map(AppointmentSlot::getSlotStart).collect(Collectors.toSet());
        List<AppointmentSlot> slots = new ArrayList<>();
        while (!cursor.plusMinutes(request.durationMinutes()).isAfter(dayEnd)) {
            LocalDateTime end = cursor.plusMinutes(request.durationMinutes());
            // Past start times (e.g. earlier today) are skipped rather than created unbookable.
            if (cursor.isAfter(now) && !existing.contains(cursor)) {
                AppointmentSlot slot = new AppointmentSlot();
                slot.setDoctor(doctor);
                slot.setSlotStart(cursor);
                slot.setSlotEnd(end);
                slot.setStatus(SlotStatus.AVAILABLE);
                slots.add(slot);
            }
            cursor = end;
        }
        List<SlotResponse> created = slotRepository.saveAll(slots).stream().map(DtoMapper::slot).toList();
        auditService.success(AuditAction.SLOTS_CREATED, "DOCTOR", doctor.getId(),
                "date=" + date + ", count=" + created.size());
        return created;
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listDoctorSlots(Long doctorId, LocalDate date) {
        LocalDate actualDate = date == null ? LocalDate.now(clock) : date;
        LocalDateTime start = actualDate.atStartOfDay();
        LocalDateTime end = actualDate.plusDays(1).atStartOfDay();
        return slotRepository.findByDoctorIdAndSlotStartBetweenOrderBySlotStartAsc(doctorId, start, end)
                .stream().map(DtoMapper::slot).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listAvailableDoctorSlots(Long doctorId) {
        return slotRepository.findByDoctorIdAndStatusAndSlotStartAfterOrderBySlotStartAsc(
                        doctorId, SlotStatus.AVAILABLE, LocalDateTime.now(clock))
                .stream().map(DtoMapper::slot).toList();
    }
}
