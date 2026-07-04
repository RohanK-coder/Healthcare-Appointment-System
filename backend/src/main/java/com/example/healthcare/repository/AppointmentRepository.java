package com.example.healthcare.repository;

import com.example.healthcare.entity.Appointment;
import com.example.healthcare.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<Appointment> findByDoctorIdOrderByCreatedAtDesc(Long doctorId);
    long countByStatus(AppointmentStatus status);

    @Query("""
        select a from Appointment a
        join fetch a.slot s
        join fetch a.patient p
        join fetch p.user
        join fetch a.doctor d
        join fetch d.user
        where a.status = 'BOOKED'
          and a.reminderSent = false
          and s.slotStart between :fromTime and :toTime
        order by s.slotStart asc
    """)
    List<Appointment> findAppointmentsNeedingReminder(@Param("fromTime") LocalDateTime fromTime,
                                                       @Param("toTime") LocalDateTime toTime);
}
