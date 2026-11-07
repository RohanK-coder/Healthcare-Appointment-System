package com.example.healthcare.notification;

import com.example.healthcare.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP delivery. In docker compose this points at Mailpit, a local inbox that never sends real mail. */
@Component
@ConditionalOnProperty(name = "app.notifications.channel", havingValue = "email")
public class EmailNotificationSender implements NotificationSender {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.fromAddress = properties.notifications().fromAddress();
    }

    @Override
    public void send(OutgoingMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.toEmail());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
