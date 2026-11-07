package com.example.healthcare.security;

import com.example.healthcare.entity.AppUser;
import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every protected endpoint x every caller type: anonymous gets 401, the wrong role gets 403,
 * and the right role gets past authorization (any status other than 401/403).
 */
class RolePermissionTest extends IntegrationTestBase {
    enum Caller { ANONYMOUS, PATIENT, DOCTOR, ADMIN }

    record Endpoint(HttpMethod method, String path, String body, Caller allowed) {
        @Override public String toString() { return method + " " + path; }
    }

    private static final String SLOTS_BODY = "{\"date\":\"2099-01-01\",\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"durationMinutes\":30}";
    private static final String DOCTOR_BODY = "{\"fullName\":\"N\",\"email\":\"n@test.local\",\"password\":\"Passw0rd123\",\"specialization\":\"S\",\"department\":\"D\"}";
    private static final String ADMIN_BODY = "{\"fullName\":\"N\",\"email\":\"a@test.local\",\"password\":\"Passw0rd123\"}";

    static List<Endpoint> endpoints() {
        return List.of(
                new Endpoint(HttpMethod.GET, "/api/patient/doctors", null, Caller.PATIENT),
                new Endpoint(HttpMethod.GET, "/api/patient/doctors/1/slots", null, Caller.PATIENT),
                new Endpoint(HttpMethod.POST, "/api/patient/appointments", "{\"slotId\":999999}", Caller.PATIENT),
                new Endpoint(HttpMethod.GET, "/api/patient/appointments", null, Caller.PATIENT),
                new Endpoint(HttpMethod.PUT, "/api/patient/appointments/999999/cancel", null, Caller.PATIENT),
                new Endpoint(HttpMethod.POST, "/api/doctor/slots", SLOTS_BODY, Caller.DOCTOR),
                new Endpoint(HttpMethod.GET, "/api/doctor/appointments", null, Caller.DOCTOR),
                new Endpoint(HttpMethod.PUT, "/api/doctor/appointments/999999/status", "{\"status\":\"COMPLETED\"}", Caller.DOCTOR),
                new Endpoint(HttpMethod.POST, "/api/doctor/appointments/999999/summary", "{\"diagnosis\":\"x\"}", Caller.DOCTOR),
                new Endpoint(HttpMethod.GET, "/api/admin/users", null, Caller.ADMIN),
                new Endpoint(HttpMethod.POST, "/api/admin/doctors", DOCTOR_BODY, Caller.ADMIN),
                new Endpoint(HttpMethod.POST, "/api/admin/admins", ADMIN_BODY, Caller.ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/appointments", null, Caller.ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/reports", null, Caller.ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/audit-logs", null, Caller.ADMIN),
                new Endpoint(HttpMethod.GET, "/api/admin/notifications", null, Caller.ADMIN),
                new Endpoint(HttpMethod.POST, "/api/admin/notifications/999999/retry", null, Caller.ADMIN),
                new Endpoint(HttpMethod.GET, "/actuator/metrics", null, Caller.ADMIN)
        );
    }

    static Stream<Arguments> matrix() {
        return endpoints().stream().flatMap(e -> Stream.of(Caller.values()).map(c -> Arguments.of(e, c)));
    }

    private Map<Caller, TestUser> users;

    @BeforeEach
    void users() {
        users = Map.of(Caller.PATIENT, patient("Pat"), Caller.DOCTOR, doctor("Doc"), Caller.ADMIN, admin("Adm"));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("matrix")
    void enforcesRoleBasedAccess(Endpoint endpoint, Caller caller) throws Exception {
        MockHttpServletRequestBuilder request = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) request = withJson(request, endpoint.body());
        if (caller != Caller.ANONYMOUS) request = auth(request, users.get(caller));

        int status = mvc.perform(request).andReturn().getResponse().getStatus();

        if (caller == Caller.ANONYMOUS) assertThat(status).isEqualTo(401);
        else if (caller == endpoint.allowed()) assertThat(status).isNotIn(401, 403);
        else assertThat(status).isEqualTo(403);
    }

    @Test
    void meIsAvailableToEveryRoleButNotAnonymous() throws Exception {
        for (TestUser user : users.values()) mvc.perform(auth(get("/api/auth/me"), user)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotCancelAnotherPatientsAppointment() throws Exception {
        TestUser doctor = users.get(Caller.DOCTOR);
        AppointmentSlot slot = slot(doctor, tomorrowAt(10));
        TestUser owner = users.get(Caller.PATIENT);
        long id = body(mvc.perform(withJson(auth(post("/api/patient/appointments"), owner), Map.of("slotId", slot.getId())))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();

        mvc.perform(auth(put("/api/patient/appointments/" + id + "/cancel"), patient("Intruder")))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotTouchAnotherDoctorsAppointment() throws Exception {
        AppointmentSlot slot = slot(users.get(Caller.DOCTOR), tomorrowAt(10));
        long id = body(mvc.perform(withJson(auth(post("/api/patient/appointments"), users.get(Caller.PATIENT)),
                Map.of("slotId", slot.getId()))).andReturn()).get("id").asLong();
        TestUser otherDoctor = doctor("Other Doc");

        mvc.perform(withJson(auth(put("/api/doctor/appointments/" + id + "/status"), otherDoctor), Map.of("status", "COMPLETED")))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(auth(post("/api/doctor/appointments/" + id + "/summary"), otherDoctor), Map.of("diagnosis", "x")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = users.get(Caller.PATIENT).token();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeactivatedUserIsRejected() throws Exception {
        TestUser patient = users.get(Caller.PATIENT);
        AppUser user = userRepository.findById(patient.id()).orElseThrow();
        user.setActive(false);
        userRepository.save(user);
        mvc.perform(auth(get("/api/auth/me"), patient)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicRegistrationCannotCreateAdminsOrDoctors() throws Exception {
        for (String role : List.of("ADMIN", "DOCTOR")) {
            String email = role.toLowerCase() + "-attempt@test.local";
            mvc.perform(withJson(post("/api/auth/register"),
                            Map.of("fullName", "Mallory", "email", email, "password", PASSWORD, "role", role)))
                    .andExpect(status().isOk());
            assertThat(userRepository.findByEmail(email).orElseThrow().getRole().name()).isEqualTo("PATIENT");
        }
    }
}
