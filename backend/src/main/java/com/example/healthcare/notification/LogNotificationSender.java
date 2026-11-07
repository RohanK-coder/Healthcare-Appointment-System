package com.example.healthcare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default channel for local development and tests: "delivers" by logging. */
@Component
@ConditionalOnProperty(name = "app.notifications.channel", havingValue = "log", matchIfMissing = true)
public class LogNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(OutgoingMessage message) {
        log.info("Notification {} to {}: {}", message.notificationId(), message.toEmail(), message.subject());
    }
}
