package com.example.healthcare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Per-client-IP token buckets: a strict one for credential endpoints (brute-force protection)
 * and a looser one for the rest of the API. In-memory, so limits are per instance.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/password");

    private final AppProperties.RateLimit config;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.config = properties.rateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || !request.getRequestURI().startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = AUTH_PATHS.contains(request.getRequestURI());
        AppProperties.Bucket limit = auth ? config.auth() : config.api();
        String key = (auth ? "auth:" : "api:") + request.getRemoteAddr();
        Bucket bucket = buckets.get(key, k -> newBucket(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit.capacity()));
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Clears all buckets; used by tests so one test's traffic cannot throttle the next. */
    public void reset() {
        buckets.invalidateAll();
    }

    private static Bucket newBucket(AppProperties.Bucket limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.window()).build())
                .build();
    }
}
