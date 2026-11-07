package com.example.healthcare.api;

import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTestBase {

    @Test
    void registerCreatesPatientAndAuditsIt() throws Exception {
        mvc.perform(withJson(post("/api/auth/register"),
                        Map.of("fullName", "New Person", "email", "New.Person@Test.Local", "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andExpect(jsonPath("$.user.email").value("new.person@test.local"));
        assertThat(patientRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findByAction(AuditAction.USER_REGISTERED)).hasSize(1);
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        TestUser existing = patient("Dup");
        mvc.perform(withJson(post("/api/auth/register"),
                        Map.of("fullName", "Dup", "email", existing.user().getEmail().toUpperCase(), "password", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordAndBadFieldsAreRejectedWithFieldErrors() throws Exception {
        mvc.perform(withJson(post("/api/auth/register"),
                        Map.of("fullName", "", "email", "not-an-email", "password", "abcdefgh", "phone", "call me")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.fullName").exists())
                .andExpect(jsonPath("$.fields.phone").exists());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericError() throws Exception {
        TestUser user = patient("Known");
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", user.user().getEmail(), "password", "Wrong12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", "nobody@test.local", "password", "Wrong12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
        assertThat(auditLogRepository.findByAction(AuditAction.LOGIN_FAILED)).hasSize(2);
    }

    @Test
    void accountLocksAfterFiveFailuresEvenForTheRightPassword() throws Exception {
        TestUser user = patient("Target");
        String email = user.user().getEmail();
        for (int i = 0; i < 5; i++) {
            mvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", "Wrong12345")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isLocked());
        assertThat(userRepository.findByEmail(email).orElseThrow().getLockedUntil()).isAfter(now().minusMinutes(1));
        assertThat(auditLogRepository.findByAction(AuditAction.LOGIN_LOCKED)).hasSize(1);
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        TestUser user = patient("Forgetful");
        String email = user.user().getEmail();
        for (int i = 0; i < 4; i++) {
            mvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", "Wrong12345")));
        }
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail(email).orElseThrow().getFailedLoginAttempts()).isZero();
        assertThat(auditLogRepository.findByAction(AuditAction.LOGIN_SUCCESS)).hasSize(1);
    }

    @Test
    void changingPasswordRevokesOldTokens() throws Exception {
        TestUser user = patient("Rotator");
        String newToken = body(mvc.perform(withJson(auth(put("/api/auth/password"), user),
                        Map.of("currentPassword", PASSWORD, "newPassword", "Brand9NewPass")))
                .andExpect(status().isOk()).andReturn()).get("token").asText();

        mvc.perform(auth(get("/api/auth/me"), user)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", user.user().getEmail(), "password", "Brand9NewPass")))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.findByAction(AuditAction.PASSWORD_CHANGED)).hasSize(1);
    }

    @Test
    void changingPasswordRequiresTheCurrentPassword() throws Exception {
        TestUser user = patient("Guess");
        mvc.perform(withJson(auth(put("/api/auth/password"), user),
                        Map.of("currentPassword", "NotIt12345", "newPassword", "Brand9NewPass")))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/auth/me"), user)).andExpect(status().isOk());
    }

    @Test
    void passwordHashesNeverAppearInResponses() throws Exception {
        TestUser admin = admin("Auditor");
        patient("Someone");
        String users = mvc.perform(auth(get("/api/admin/users"), admin)).andReturn().getResponse().getContentAsString();
        String me = mvc.perform(auth(get("/api/auth/me"), admin)).andReturn().getResponse().getContentAsString();
        assertThat(users + me).doesNotContain("$2a$").doesNotContainIgnoringCase("password");
    }
}
