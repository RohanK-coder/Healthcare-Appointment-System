package com.example.healthcare.notification;

import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures queue overhead (enqueue + claim + status updates) with an in-memory sender, so the
 * numbers exclude SMTP latency. Run explicitly:
 *   mvn test -Dgroups=benchmark -Dsurefire.excludedGroups= -Dtest=ReminderThroughputBenchmark
 */
@Tag("benchmark")
class ReminderThroughputBenchmark extends IntegrationTestBase {
    private static final int JOBS = 5_000;

    @Autowired ReminderService reminderService;

    @ParameterizedTest(name = "{0} worker(s)")
    @ValueSource(ints = {1, 4})
    void processesReminderBacklog(int workers) throws Exception {
        TestUser doctor = doctor("Dr Bench");
        TestUser patient = patient("Bench Patient");
        seedBookedAppointments(doctor.profileId(), patient.profileId());

        long t0 = System.nanoTime();
        int enqueued = reminderService.enqueueDueReminders();
        long enqueueMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(enqueued).isEqualTo(JOBS);

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        long t1 = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            futures.add(pool.submit(() -> { while (reminderService.processDueBatch() > 0) { } return null; }));
        }
        for (Future<?> f : futures) f.get();
        long processMs = (System.nanoTime() - t1) / 1_000_000;
        pool.shutdown();

        assertThat(sender.delivered()).hasSize(JOBS).doesNotHaveDuplicates();
        assertThat(notificationRepository.findAll()).allMatch(n -> n.getStatus() == NotificationStatus.SENT);
        System.out.printf("BENCHMARK reminders: jobs=%d workers=%d enqueue=%dms process=%dms throughput=%.0f jobs/s%n",
                JOBS, workers, enqueueMs, processMs, JOBS * 1000.0 / Math.max(processMs, 1));
    }

    /** Bulk insert via JDBC; going through JPA would make setup dominate the run time. */
    private void seedBookedAppointments(Long doctorId, Long patientId) {
        var start = now().plusHours(1);
        List<Object[]> slots = new ArrayList<>();
        for (int i = 0; i < JOBS; i++) {
            slots.add(new Object[]{doctorId, Timestamp.valueOf(start.plusSeconds(i)), Timestamp.valueOf(start.plusSeconds(i).plusMinutes(30))});
        }
        jdbc.batchUpdate("INSERT INTO appointment_slots (doctor_id, slot_start, slot_end, status) VALUES (?, ?, ?, 'BOOKED')", slots);
        jdbc.update("""
            INSERT INTO appointments (patient_id, doctor_id, slot_id, status)
            SELECT ?, doctor_id, id, 'BOOKED' FROM appointment_slots WHERE doctor_id = ?
            """, patientId, doctorId);
    }
}
