package com.example.healthcare.support;

import com.example.healthcare.notification.NotificationSender;
import com.example.healthcare.notification.OutgoingMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Test double: records deliveries and can be told to fail N times, or always. */
public class ProgrammableNotificationSender implements NotificationSender {
    private final List<Long> delivered = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicBoolean alwaysFail = new AtomicBoolean();

    @Override
    public void send(OutgoingMessage message) {
        if (alwaysFail.get() || failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("simulated SMTP outage");
        }
        delivered.add(message.notificationId());
    }

    public void failNext(int times) { failuresRemaining.set(times); }
    public void failAlways(boolean value) { alwaysFail.set(value); }
    public List<Long> delivered() { return delivered; }

    public void reset() {
        delivered.clear();
        failuresRemaining.set(0);
        alwaysFail.set(false);
    }
}
