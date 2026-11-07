package com.example.healthcare.notification;

import com.example.healthcare.config.AppProperties;
import com.example.healthcare.dto.DomainDtos.NotificationResponse;
import com.example.healthcare.dto.DomainDtos.PageResponse;
import com.example.healthcare.entity.Notification;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.NotificationRepository;
import com.example.healthcare.service.AuditService;
import com.example.healthcare.service.DtoMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reminder pipeline backed by the notifications table as a job queue:
 * <ol>
 *   <li>{@link #enqueueDueReminders()} inserts one PENDING job per booked appointment starting
 *       within the reminder horizon (idempotent via a unique constraint).</li>
 *   <li>{@link #processDueBatch()} claims due jobs with FOR UPDATE SKIP LOCKED, sends each one
 *       outside any transaction, then records SENT, or FAILED with exponential backoff, or DEAD
 *       once attempts are exhausted.</li>
 * </ol>
 * Jobs whose appointment is no longer BOOKED are marked SKIPPED instead of sent.
 */
@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationSender sender;
    private final AuditService auditService;
    private final AppProperties.Notifications config;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter deadCounter;
    private final Timer sendTimer;

    public ReminderService(NotificationRepository notificationRepository,
                           NotificationSender sender,
                           AuditService auditService,
                           AppProperties properties,
                           PlatformTransactionManager transactionManager,
                           Clock clock,
                           MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.sender = sender;
        this.auditService = auditService;
        this.config = properties.notifications();
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.sentCounter = meterRegistry.counter("notifications.processed", "result", "sent");
        this.failedCounter = meterRegistry.counter("notifications.processed", "result", "failed");
        this.deadCounter = meterRegistry.counter("notifications.processed", "result", "dead");
        this.sendTimer = meterRegistry.timer("notifications.send");
    }

    @Transactional
    public int enqueueDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        int created = notificationRepository.enqueueDueReminders(now, now.plus(config.reminderHorizon()));
        if (created > 0) log.info("Enqueued {} reminder notifications", created);
        return created;
    }

    /** Claims and processes one batch. Returns the number of jobs claimed. */
    public int processDueBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> claimed = tx.execute(status -> {
            notificationRepository.releaseStaleLeases(now, now.minus(config.leaseTimeout()));
            return notificationRepository.claimDueBatch(now, config.batchSize());
        });
        if (claimed == null || claimed.isEmpty()) return 0;

        long started = System.nanoTime();
        for (Long id : claimed) {
            processOne(id);
        }
        log.info("Processed {} notifications in {} ms", claimed.size(), (System.nanoTime() - started) / 1_000_000);
        return claimed.size();
    }

    private void processOne(Long id) {
        OutgoingMessage message = tx.execute(status -> render(id));
        if (message == null) return; // skipped

        try {
            sendTimer.recordCallable(() -> {
                sender.send(message);
                return null;
            });
            tx.executeWithoutResult(status -> markSent(id));
            sentCounter.increment();
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(status -> markFailed(id, error));
        }
    }

    private OutgoingMessage render(Long id) {
        Notification n = notificationRepository.findWithDetailsByIdIn(List.of(id)).getFirst();
        if (n.getAppointment().getStatus() != AppointmentStatus.BOOKED) {
            n.setStatus(NotificationStatus.SKIPPED);
            n.setLockedAt(null);
            n.setLastError("Appointment is " + n.getAppointment().getStatus());
            return null;
        }
        return new OutgoingMessage(n.getId(), n.getRecipientUser().getEmail(), n.getRecipientUser().getFullName(),
                "Appointment reminder",
                "Hello " + n.getRecipientUser().getFullName() + ",\n\n" + n.getMessage()
                        + "\n\nThis message comes from a demo system with synthetic data.");
    }

    private void markSent(Long id) {
        Notification n = notificationRepository.findWithDetailsByIdIn(List.of(id)).getFirst();
        n.setStatus(NotificationStatus.SENT);
        n.setSentAt(LocalDateTime.now(clock));
        n.setLockedAt(null);
        n.setLastError(null);
        n.getAppointment().setReminderSent(true);
    }

    private void markFailed(Long id, String error) {
        Notification n = notificationRepository.findById(id).orElseThrow();
        n.setLockedAt(null);
        n.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
        if (n.getAttempts() >= config.maxAttempts()) {
            n.setStatus(NotificationStatus.DEAD);
            deadCounter.increment();
            log.warn("Notification {} is dead after {} attempts: {}", id, n.getAttempts(), error);
            auditService.failure(null, null, AuditAction.NOTIFICATION_DEAD, "notificationId=" + id + ", attempts=" + n.getAttempts());
        } else {
            n.setStatus(NotificationStatus.FAILED);
            n.setNextAttemptAt(LocalDateTime.now(clock).plus(backoff(n.getAttempts(), config.backoffBase(), config.backoffMax())));
            failedCounter.increment();
            log.info("Notification {} failed (attempt {}), retrying at {}", id, n.getAttempts(), n.getNextAttemptAt());
        }
    }

    /** base * 2^(attempt-1), capped at max, plus up to 10% jitter so retries from many jobs spread out. */
    static Duration backoff(int attempt, Duration base, Duration max) {
        long exp = base.toMillis() * (1L << Math.min(Math.max(attempt - 1, 0), 20));
        long capped = Math.min(exp, max.toMillis());
        long jitter = capped == 0 ? 0 : ThreadLocalRandom.current().nextLong(capped / 10 + 1);
        return Duration.ofMillis(capped + jitter);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(NotificationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<Notification> result = status == null
                ? notificationRepository.findAllByOrderByCreatedAtDesc(pageable)
                : notificationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return new PageResponse<>(result.map(DtoMapper::notification).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Manually requeues a DEAD or FAILED job (e.g. after fixing SMTP credentials). */
    @Transactional
    public NotificationResponse retry(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        if (n.getStatus() != NotificationStatus.DEAD && n.getStatus() != NotificationStatus.FAILED) {
            throw ApiException.badRequest("Only DEAD or FAILED notifications can be retried.");
        }
        n.setStatus(NotificationStatus.PENDING);
        n.setAttempts(0);
        n.setNextAttemptAt(LocalDateTime.now(clock));
        auditService.success(AuditAction.NOTIFICATION_RETRIED, "NOTIFICATION", id, null);
        return DtoMapper.notification(n);
    }
}
