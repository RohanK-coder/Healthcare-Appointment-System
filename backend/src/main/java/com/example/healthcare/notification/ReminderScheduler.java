package com.example.healthcare.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the reminder queue. Safe to run on several instances at once: enqueue is idempotent
 * and workers claim disjoint batches with SKIP LOCKED. Disabled in tests, which call the
 * service directly.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {
    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.enqueue-interval}", initialDelayString = "PT10S")
    public void enqueue() {
        reminderService.enqueueDueReminders();
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval}", initialDelayString = "PT15S")
    public void work() {
        // Drain a backlog quickly, but bound the work per tick so one tick cannot run forever.
        int claimed;
        int rounds = 0;
        do {
            claimed = reminderService.processDueBatch();
        } while (claimed > 0 && ++rounds < 20);
    }
}
