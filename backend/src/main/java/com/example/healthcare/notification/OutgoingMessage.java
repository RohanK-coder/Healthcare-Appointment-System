package com.example.healthcare.notification;

/** A rendered message, detached from JPA so it can be sent outside any transaction. */
public record OutgoingMessage(Long notificationId, String toEmail, String toName, String subject, String body) {}
