package com.example.healthcare.repository;

import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") Long id);

    List<AppointmentSlot> findByDoctorIdAndSlotStartBetweenOrderBySlotStartAsc(
            Long doctorId, LocalDateTime start, LocalDateTime end);

    List<AppointmentSlot> findByDoctorIdAndStatusAndSlotStartAfterOrderBySlotStartAsc(
            Long doctorId, SlotStatus status, LocalDateTime after);

    boolean existsByDoctorIdAndSlotStart(Long doctorId, LocalDateTime slotStart);
}
