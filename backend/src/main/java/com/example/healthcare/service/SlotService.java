package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.CreateSlotsRequest;
import com.example.healthcare.dto.DomainDtos.SlotResponse;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.entity.Doctor;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.AppointmentSlotRepository;
import com.example.healthcare.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlotService {
    private final AppointmentSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;

    public SlotService(AppointmentSlotRepository slotRepository, DoctorRepository doctorRepository, CurrentUserService currentUserService) {
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public List<SlotResponse> createMySlots(CreateSlotsRequest request) {
        AppUser user = currentUserService.currentUser();
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("Doctor profile not found."));

        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("End time must be after start time.");
        }

        LocalDate date = request.date();
        LocalTime cursor = request.startTime();
        List<AppointmentSlot> slots = new ArrayList<>();
        while (cursor.plusMinutes(request.durationMinutes()).compareTo(request.endTime()) <= 0) {
            LocalDateTime start = LocalDateTime.of(date, cursor);
            LocalDateTime end = start.plusMinutes(request.durationMinutes());
            if (!slotRepository.existsByDoctorIdAndSlotStart(doctor.getId(), start)) {
                AppointmentSlot slot = new AppointmentSlot();
                slot.setDoctor(doctor);
                slot.setSlotStart(start);
                slot.setSlotEnd(end);
                slot.setStatus(SlotStatus.AVAILABLE);
                slots.add(slot);
            }
            cursor = cursor.plusMinutes(request.durationMinutes());
        }
        return slotRepository.saveAll(slots).stream().map(DtoMapper::slot).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listDoctorSlots(Long doctorId, LocalDate date) {
        LocalDate actualDate = date == null ? LocalDate.now() : date;
        LocalDateTime start = actualDate.atStartOfDay();
        LocalDateTime end = actualDate.plusDays(1).atStartOfDay();
        return slotRepository.findByDoctorIdAndSlotStartBetweenOrderBySlotStartAsc(doctorId, start, end)
                .stream().map(DtoMapper::slot).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> listAvailableDoctorSlots(Long doctorId) {
        return slotRepository.findByDoctorIdAndStatusAndSlotStartAfterOrderBySlotStartAsc(
                        doctorId, SlotStatus.AVAILABLE, LocalDateTime.now())
                .stream().map(DtoMapper::slot).toList();
    }
}
