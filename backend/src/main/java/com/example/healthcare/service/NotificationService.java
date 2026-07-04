package com.example.healthcare.service;

import com.example.healthcare.entity.Appointment;
import com.example.healthcare.entity.Notification;
import com.example.healthcare.enums.NotificationStatus;
import com.example.healthcare.repository.AppointmentRepository;
import com.example.healthcare.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;

    public NotificationService(NotificationRepository notificationRepository, AppointmentRepository appointmentRepository) {
        this.notificationRepository = notificationRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    @Scheduled(fixedRateString = "${app.reminders.fixed-rate-ms:60000}")
    public void createAppointmentReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next24Hours = now.plusHours(24);
        List<Appointment> appointments = appointmentRepository.findAppointmentsNeedingReminder(now, next24Hours);

        long startNanos = System.nanoTime();
        for (Appointment appointment : appointments) {
            String message = "Reminder: appointment with Dr. "
                    + appointment.getDoctor().getUser().getFullName()
                    + " at " + appointment.getSlot().getSlotStart();
            Notification notification = new Notification();
            notification.setAppointment(appointment);
            notification.setRecipientUser(appointment.getPatient().getUser());
            notification.setMessage(message);
            notification.setStatus(NotificationStatus.SENT);
            notificationRepository.save(notification);
            appointment.setReminderSent(true);
        }
        appointmentRepository.saveAll(appointments);
        if (!appointments.isEmpty()) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("Processed {} appointment reminders in {} ms", appointments.size(), elapsedMs);
        }
    }
}
