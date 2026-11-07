package com.example.healthcare.security;

import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Test profile: 10 auth requests per minute per IP. */
class RateLimitTest extends IntegrationTestBase {

    @Test
    void credentialEndpointsAreThrottledPerClientIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(login("203.0.113.7")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("203.0.113.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // A different client is unaffected.
        mvc.perform(login("198.51.100.20")).andExpect(status().isUnauthorized());
    }

    @Test
    void authBucketDoesNotThrottleRegularApiCalls() throws Exception {
        for (int i = 0; i < 11; i++) mvc.perform(login("203.0.113.8"));
        TestUser patient = patient("Still Works");
        mvc.perform(auth(get("/api/patient/doctors"), patient).with(r -> { r.setRemoteAddr("203.0.113.8"); return r; }))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    private MockHttpServletRequestBuilder login(String ip) throws Exception {
        return withJson(post("/api/auth/login"), Map.of("email", "nobody@test.local", "password", "Wrong12345"))
                .with(r -> { r.setRemoteAddr(ip); return r; });
    }
}
