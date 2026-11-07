package com.example.healthcare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Slot times are stored as clinic wall-clock times (TIMESTAMP without zone). The JVM default
 * zone is aligned to the clinic zone so that every "now" (services, entity timestamps, Bean
 * Validation's @FutureOrPresent) agrees, whatever timezone the host machine is in.
 */
@Configuration
public class TimeConfig {
    @Bean
    public Clock clinicClock(AppProperties properties) {
        ZoneId zone = ZoneId.of(properties.clinicZone());
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        return Clock.system(zone);
    }
}
