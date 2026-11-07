package com.example.healthcare.repository;

import com.example.healthcare.entity.Doctor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Doctor> findByUserActiveTrueOrderByUserFullNameAsc();
}
