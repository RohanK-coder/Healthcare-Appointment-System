package com.example.healthcare.support;

import com.example.healthcare.config.RateLimitFilter;
import com.example.healthcare.entity.*;
import com.example.healthcare.enums.Role;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.repository.*;
import com.example.healthcare.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Boots the full application against a real PostgreSQL (Testcontainers), so row locks, partial
 * unique indexes and SKIP LOCKED behave exactly as in production. One container and one Spring
 * context are shared by every test class; tables are truncated before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestBeans.class)
public abstract class IntegrationTestBase {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    public static final String PASSWORD = "Passw0rd123";

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        ProgrammableNotificationSender programmableNotificationSender() {
            return new ProgrammableNotificationSender();
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtService jwtService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected Clock clock;
    @Autowired protected AppUserRepository userRepository;
    @Autowired protected DoctorRepository doctorRepository;
    @Autowired protected PatientRepository patientRepository;
    @Autowired protected AppointmentSlotRepository slotRepository;
    @Autowired protected AppointmentRepository appointmentRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected AuditLogRepository auditLogRepository;
    @Autowired protected RateLimitFilter rateLimitFilter;
    @Autowired protected ProgrammableNotificationSender sender;

    private int sequence = 0;

    @BeforeEach
    void resetState() {
        jdbc.execute("""
            TRUNCATE app_users, doctors, patients, appointment_slots, appointments,
                     appointment_summaries, notifications, audit_logs RESTART IDENTITY CASCADE
            """);
        rateLimitFilter.reset();
        sender.reset();
    }

    public record TestUser(AppUser user, Long profileId, String token) {
        public Long id() { return user.getId(); }
    }

    protected TestUser patient(String name) {
        AppUser user = user(name, Role.PATIENT);
        Patient patient = new Patient();
        patient.setUser(user);
        patient = patientRepository.save(patient);
        return new TestUser(user, patient.getId(), jwtService.generateToken(user));
    }

    protected TestUser doctor(String name) {
        AppUser user = user(name, Role.DOCTOR);
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialization("General Medicine");
        doctor.setDepartment("Primary Care");
        doctor = doctorRepository.save(doctor);
        return new TestUser(user, doctor.getId(), jwtService.generateToken(user));
    }

    protected TestUser admin(String name) {
        AppUser user = user(name, Role.ADMIN);
        return new TestUser(user, null, jwtService.generateToken(user));
    }

    private AppUser user(String name, Role role) {
        AppUser user = new AppUser();
        user.setFullName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "." + (++sequence) + "@test.local");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        return userRepository.save(user);
    }

    protected AppointmentSlot slot(TestUser doctor, LocalDateTime start) {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setDoctor(doctorRepository.getReferenceById(doctor.profileId()));
        slot.setSlotStart(start);
        slot.setSlotEnd(start.plusMinutes(30));
        slot.setStatus(SlotStatus.AVAILABLE);
        return slotRepository.save(slot);
    }

    protected LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    protected LocalDateTime tomorrowAt(int hour) {
        return LocalDate.now(clock).plusDays(1).atTime(hour, 0);
    }

    // ---- HTTP helpers ----

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, TestUser user) {
        return request.header("Authorization", "Bearer " + user.token());
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
