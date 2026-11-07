package com.example.healthcare.entity;

import com.example.healthcare.enums.AuditAction;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    public enum Outcome { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_email", length = 160, updatable = false)
    private String actorEmail;

    @Column(name = "actor_role", length = 30, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Outcome outcome;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
