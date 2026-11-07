package com.example.healthcare.notification;

import com.example.healthcare.entity.Appointment;
import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.entity.Notification;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test profile: max-attempts 3, zero backoff so retries are immediately due. */
class ReminderQueueTest extends IntegrationTestBase {
    @Autowired ReminderService reminderService;

    @Test
    void enqueuesOnlyBookedAppointmentsWithinTheHorizon_andIsIdempotent() throws Exception {
        TestUser doctor = doctor("Dr Queue");
        TestUser patient = patient("Queued");
        Appointment soon = book(patient, slot(doctor, now().plusHours(3)));
        book(patient, slot(doctor, now().plusDays(3)));                       // beyond 24h
        Appointment canceled = book(patient, slot(doctor, now().plusHours(5)));
        canceled.setStatus(AppointmentStatus.CANCELED);
        appointmentRepository.save(canceled);

        assertThat(reminderService.enqueueDueReminders()).isEqualTo(1);
        assertThat(reminderService.enqueueDueReminders()).isZero();
        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getAppointment().getId()).isEqualTo(soon.getId());
        assertThat(all.getFirst().getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void deliversAndMarksAppointmentReminded() throws Exception {
        Appointment appointment = book(patient("Happy"), slot(doctor("Dr Ok"), now().plusHours(2)));
        reminderService.enqueueDueReminders();

        assertThat(reminderService.processDueBatch()).isEqualTo(1);

        Notification n = notificationRepository.findAll().getFirst();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getSentAt()).isNotNull();
        assertThat(sender.delivered()).containsExactly(n.getId());
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().isReminderSent()).isTrue();
        assertThat(reminderService.processDueBatch()).isZero();
    }

    @Test
    void transientFailuresAreRetriedUntilDelivered() throws Exception {
        book(patient("Flaky"), slot(doctor("Dr Flaky"), now().plusHours(2)));
        reminderService.enqueueDueReminders();
        sender.failNext(2);

        reminderService.processDueBatch();
        Notification afterFirst = notificationRepository.findAll().getFirst();
        assertThat(afterFirst.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(afterFirst.getLastError()).contains("simulated SMTP outage");

        reminderService.processDueBatch();
        reminderService.processDueBatch();

        Notification n = notificationRepository.findAll().getFirst();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(3);
        assertThat(n.getLastError()).isNull();
        assertThat(sender.delivered()).hasSize(1);
    }

    @Test
    void permanentFailureGoesDeadAndCanBeRequeuedByAdmin() throws Exception {
        book(patient("Doomed"), slot(doctor("Dr Down"), now().plusHours(2)));
        reminderService.enqueueDueReminders();
        sender.failAlways(true);

        for (int i = 0; i < 5; i++) reminderService.processDueBatch();

        Notification dead = notificationRepository.findAll().getFirst();
        assertThat(dead.getStatus()).isEqualTo(NotificationStatus.DEAD);
        assertThat(dead.getAttempts()).isEqualTo(3);
        assertThat(auditLogRepository.findByAction(AuditAction.NOTIFICATION_DEAD)).hasSize(1);

        sender.failAlways(false);
        mvc.perform(auth(post("/api/admin/notifications/" + dead.getId() + "/retry"), admin("Ops")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        reminderService.processDueBatch();
        assertThat(notificationRepository.findById(dead.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void appointmentCanceledAfterEnqueueIsSkippedNotSent() throws Exception {
        Appointment appointment = book(patient("Changed Mind"), slot(doctor("Dr Skip"), now().plusHours(2)));
        reminderService.enqueueDueReminders();
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointmentRepository.save(appointment);

        reminderService.processDueBatch();

        assertThat(notificationRepository.findAll().getFirst().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(sender.delivered()).isEmpty();
    }

    @Test
    void concurrentWorkersNeverDeliverTheSameJobTwice() throws Exception {
        TestUser doctor = doctor("Dr Busy");
        TestUser patient = patient("Many");
        LocalDateTime base = now().plusHours(1);
        int jobs = 150;
        for (int i = 0; i < jobs; i++) book(patient, slot(doctor, base.plusMinutes(i)));
        assertThat(reminderService.enqueueDueReminders()).isEqualTo(jobs);

        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            futures.add(pool.submit(() -> {
                start.await();
                while (reminderService.processDueBatch() > 0) { /* drain */ }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(sender.delivered()).hasSize(jobs);
        assertThat(new HashSet<>(sender.delivered())).hasSize(jobs);
        assertThat(notificationRepository.findAll()).allMatch(n -> n.getStatus() == NotificationStatus.SENT && n.getAttempts() == 1);
    }

    @Test
    void jobsStuckInSendingByACrashedWorkerAreRecovered() throws Exception {
        book(patient("Orphan"), slot(doctor("Dr Crash"), now().plusHours(2)));
        reminderService.enqueueDueReminders();
        // Simulate a worker that claimed the job and died 10 minutes ago.
        jdbc.update("UPDATE notifications SET status = 'SENDING', attempts = 1, locked_at = ?", now().minusMinutes(10));

        reminderService.processDueBatch();

        Notification n = notificationRepository.findAll().getFirst();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(2);
    }

    private Appointment book(TestUser patient, AppointmentSlot slot) {
        Appointment a = new Appointment();
        a.setPatient(patientRepository.getReferenceById(patient.profileId()));
        a.setDoctor(slot.getDoctor());
        a.setSlot(slot);
        a.setStatus(AppointmentStatus.BOOKED);
        return appointmentRepository.save(a);
    }
}
