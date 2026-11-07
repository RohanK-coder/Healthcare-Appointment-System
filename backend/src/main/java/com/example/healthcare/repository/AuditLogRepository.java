package com.example.healthcare.repository;

import com.example.healthcare.entity.AuditLog;
import com.example.healthcare.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
    Page<AuditLog> findByActionOrderByOccurredAtDescIdDesc(AuditAction action, Pageable pageable);
    List<AuditLog> findByAction(AuditAction action);
}
