package com.example.healthcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String clinicZone,
        Security security,
        RateLimit rateLimit,
        Notifications notifications
) {
    public record Security(int bcryptStrength, int maxFailedLogins, Duration lockoutDuration) {}

    public record RateLimit(boolean enabled, Bucket auth, Bucket api) {}

    /** capacity requests per window, refilled greedily. */
    public record Bucket(int capacity, Duration window) {}

    public record Notifications(
            boolean schedulerEnabled,
            String channel,
            String fromAddress,
            Duration enqueueInterval,
            Duration pollInterval,
            Duration reminderHorizon,
            int batchSize,
            int maxAttempts,
            Duration backoffBase,
            Duration backoffMax,
            Duration leaseTimeout
    ) {}
}
