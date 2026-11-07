package com.example.healthcare.service;

import com.example.healthcare.dto.DomainDtos.AuditLogResponse;
import com.example.healthcare.dto.DomainDtos.PageResponse;
import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.AuditLog;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.repository.AuditLogRepository;
import com.example.healthcare.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Append-only audit trail of security-relevant and state-changing actions.
 * Details never include clinical free text (diagnosis, notes, prescriptions).
 */
@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Records a successful action by the authenticated user, inside the caller's transaction. */
    @Transactional
    public void success(AuditAction action, String entityType, Long entityId, String details) {
        save(currentActor(), action, entityType, entityId, AuditLog.Outcome.SUCCESS, details);
    }

    /** Records an action on behalf of an explicit actor (e.g. login, before a security context exists). */
    @Transactional
    public void successFor(AppUser actor, AuditAction action, String entityType, Long entityId, String details) {
        save(actor, action, entityType, entityId, AuditLog.Outcome.SUCCESS, details);
    }

    /** Failures commit independently so they survive the caller's rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(AppUser actor, String attemptedEmail, AuditAction action, String details) {
        AuditLog entry = build(actor, action, null, null, AuditLog.Outcome.FAILURE, details);
        if (actor == null) entry.setActorEmail(attemptedEmail);
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(AuditAction action, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<AuditLog> result = action == null
                ? auditLogRepository.findAllByOrderByOccurredAtDescIdDesc(pageable)
                : auditLogRepository.findByActionOrderByOccurredAtDescIdDesc(action, pageable);
        return new PageResponse<>(result.map(DtoMapper::auditLog).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private void save(AppUser actor, AuditAction action, String entityType, Long entityId,
                      AuditLog.Outcome outcome, String details) {
        auditLogRepository.save(build(actor, action, entityType, entityId, outcome, details));
    }

    private AuditLog build(AppUser actor, AuditAction action, String entityType, Long entityId,
                           AuditLog.Outcome outcome, String details) {
        AuditLog entry = new AuditLog();
        if (actor != null) {
            entry.setActorUserId(actor.getId());
            entry.setActorEmail(actor.getEmail());
            entry.setActorRole(actor.getRole().name());
        }
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOutcome(outcome);
        entry.setIpAddress(clientIp());
        entry.setDetails(details);
        return entry;
    }

    private static AppUser currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal principal ? principal.getUser() : null;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
