package com.example.healthcare.repository;

import com.example.healthcare.entity.Appointment;
import com.example.healthcare.enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    // Every list view renders patient, doctor and slot, so fetch them in the same query.
    String WITH_DETAILS = """
        select a from Appointment a
        join fetch a.slot s
        join fetch a.patient p
        join fetch p.user
        join fetch a.doctor d
        join fetch d.user
        """;

    @Query(WITH_DETAILS + " where p.id = :patientId order by s.slotStart desc")
    List<Appointment> findDetailedByPatientId(@Param("patientId") Long patientId);

    @Query(WITH_DETAILS + " where d.id = :doctorId order by s.slotStart desc")
    List<Appointment> findDetailedByDoctorId(@Param("doctorId") Long doctorId);

    @Query(WITH_DETAILS + " order by s.slotStart desc")
    List<Appointment> findAllDetailed();

    /** Row lock for state transitions, so two concurrent cancels cannot both pass the status check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(AppointmentStatus status);

    long countBySlotIdAndStatusNot(Long slotId, AppointmentStatus status);
}
