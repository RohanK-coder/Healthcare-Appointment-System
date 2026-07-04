package com.example.healthcare.repository;

import com.example.healthcare.entity.AppointmentSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentSummaryRepository extends JpaRepository<AppointmentSummary, Long> {
    Optional<AppointmentSummary> findByAppointmentId(Long appointmentId);
}
