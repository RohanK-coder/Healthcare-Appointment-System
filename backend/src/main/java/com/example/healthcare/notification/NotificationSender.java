package com.example.healthcare.notification;

/** Delivery channel. Implementations throw on failure; the queue handles retries. */
public interface NotificationSender {
    void send(OutgoingMessage message) throws Exception;
}
