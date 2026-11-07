package com.example.healthcare.repository;

import com.example.healthcare.entity.Notification;
import com.example.healthcare.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    /**
     * Set-based enqueue of 24h reminders. The unique (appointment_id, type) constraint plus
     * ON CONFLICT DO NOTHING makes this idempotent and safe to run from several instances.
     */
    @Modifying
    @Query(value = """
        INSERT INTO notifications (appointment_id, recipient_user_id, type, message, status,
                                   attempts, next_attempt_at, created_at, updated_at)
        SELECT a.id, p.user_id, 'REMINDER_24H',
               'Reminder: appointment with Dr. ' || du.full_name || ' at ' || to_char(s.slot_start, 'YYYY-MM-DD HH24:MI'),
               'PENDING', 0, :now, :now, :now
        FROM appointments a
        JOIN appointment_slots s ON s.id = a.slot_id
        JOIN patients p ON p.id = a.patient_id
        JOIN doctors d ON d.id = a.doctor_id
        JOIN app_users du ON du.id = d.user_id
        WHERE a.status = 'BOOKED'
          AND s.slot_start BETWEEN :now AND :horizon
        ON CONFLICT (appointment_id, type) DO NOTHING
        """, nativeQuery = true)
    int enqueueDueReminders(@Param("now") LocalDateTime now, @Param("horizon") LocalDateTime horizon);

    /**
     * Claims a batch of due jobs. FOR UPDATE SKIP LOCKED lets concurrent workers each take a
     * disjoint batch without blocking each other.
     */
    // Not @Modifying: UPDATE ... RETURNING produces a result set, so it runs as a query.
    @Query(value = """
        UPDATE notifications SET status = 'SENDING', locked_at = :now, attempts = attempts + 1, updated_at = :now
        WHERE id IN (
            SELECT id FROM notifications
            WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id
        """, nativeQuery = true)
    List<Long> claimDueBatch(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    /** Jobs left in SENDING by a crashed worker go back to the retry queue. */
    @Modifying
    @Query(value = """
        UPDATE notifications SET status = 'FAILED', locked_at = NULL, next_attempt_at = :now,
               last_error = 'Worker lease expired', updated_at = :now
        WHERE status = 'SENDING' AND locked_at < :staleBefore
        """, nativeQuery = true)
    int releaseStaleLeases(@Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);

    @EntityGraph(attributePaths = {"appointment", "appointment.slot", "recipientUser"})
    @Query("select n from Notification n where n.id in :ids")
    List<Notification> findWithDetailsByIdIn(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {"recipientUser"})
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"recipientUser"})
    Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    interface StatusCount {
        NotificationStatus getStatus();
        long getTotal();
    }

    @Query("select n.status as status, count(n) as total from Notification n group by n.status")
    List<StatusCount> countByStatus();

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);
}
