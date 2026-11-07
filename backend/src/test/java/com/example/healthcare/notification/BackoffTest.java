package com.example.healthcare.notification;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofMinutes(30);

    @RepeatedTest(20)
    void doublesEachAttemptWithAtMostTenPercentJitter() {
        for (int attempt = 1; attempt <= 6; attempt++) {
            long expected = 30_000L << (attempt - 1);
            long actual = ReminderService.backoff(attempt, BASE, MAX).toMillis();
            assertThat(actual).isBetween(expected, expected + expected / 10);
        }
    }

    @Test
    void isCappedAtTheMaximum() {
        long capped = ReminderService.backoff(50, BASE, MAX).toMillis();
        assertThat(capped).isBetween(MAX.toMillis(), MAX.toMillis() + MAX.toMillis() / 10);
    }

    @Test
    void zeroBaseMeansImmediateRetry() {
        assertThat(ReminderService.backoff(3, Duration.ZERO, MAX)).isZero();
    }
}
