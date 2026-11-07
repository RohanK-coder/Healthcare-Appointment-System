package com.example.healthcare.api;

import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.entity.AuditLog;
import com.example.healthcare.enums.AuditAction;
import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentApiTest extends IntegrationTestBase {

    @Test
    void doctorGeneratesSlotsAndPatientSeesThem() throws Exception {
        TestUser doctor = doctor("Dr Slots");
        String date = LocalDate.now(clock).plusDays(2).toString();
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", date, "startTime", "09:00", "endTime", "11:00", "durationMinutes", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
        // Regenerating the same range is idempotent.
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", date, "startTime", "09:00", "endTime", "11:00", "durationMinutes", 30)))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(auth(get("/api/patient/doctors/" + doctor.profileId() + "/slots?availableOnly=true"), patient("P")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void slotRangeEndingNearMidnightTerminates() throws Exception {
        // Regression: LocalTime arithmetic used to wrap past midnight and loop forever.
        TestUser doctor = doctor("Dr Late");
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", LocalDate.now(clock).plusDays(1).toString(),
                                "startTime", "23:00", "endTime", "23:59", "durationMinutes", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidSlotRequestsAreRejected() throws Exception {
        TestUser doctor = doctor("Dr Invalid");
        String date = LocalDate.now(clock).plusDays(1).toString();
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", date, "startTime", "10:00", "endTime", "09:00", "durationMinutes", 30)))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", date, "startTime", "09:00", "endTime", "10:00", "durationMinutes", 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.durationMinutes").exists());
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", LocalDate.now(clock).minusDays(1).toString(), "startTime", "09:00", "endTime", "10:00", "durationMinutes", 30)))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(auth(post("/api/doctor/slots"), doctor),
                        Map.of("date", LocalDate.now(clock).plusYears(2).toString(), "startTime", "09:00", "endTime", "10:00", "durationMinutes", 30)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonAndBadPathTypesAreBadRequests() throws Exception {
        TestUser patient = patient("Sloppy");
        mvc.perform(withJson(auth(post("/api/patient/appointments"), patient), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request."));
        mvc.perform(auth(put("/api/patient/appointments/abc/cancel"), patient))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(auth(post("/api/patient/appointments"), patient), Map.of("slotId", 1, "reason", "x".repeat(501))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullVisitLifecycleWithSummaryAndAudit() throws Exception {
        TestUser doctor = doctor("Dr Flow");
        TestUser patient = patient("Flow Patient");
        AppointmentSlot slot = slot(doctor, tomorrowAt(14));

        long id = body(mvc.perform(withJson(auth(post("/api/patient/appointments"), patient),
                Map.of("slotId", slot.getId(), "reason", "Checkup"))).andReturn()).get("id").asLong();

        mvc.perform(withJson(auth(post("/api/doctor/appointments/" + id + "/summary"), doctor),
                        Map.of("diagnosis", "SECRET-DIAGNOSIS", "prescription", "Rest", "doctorNotes", "Fine")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.diagnosis").value("SECRET-DIAGNOSIS"));

        mvc.perform(auth(get("/api/patient/appointments"), patient))
                .andExpect(jsonPath("$[0].summary.prescription").value("Rest"));

        // Completed appointments are final.
        mvc.perform(auth(put("/api/patient/appointments/" + id + "/cancel"), patient)).andExpect(status().isBadRequest());
        mvc.perform(withJson(auth(put("/api/doctor/appointments/" + id + "/status"), doctor), Map.of("status", "NO_SHOW")))
                .andExpect(status().isBadRequest());

        List<AuditLog> audit = auditLogRepository.findAll();
        assertThat(audit).extracting(AuditLog::getAction)
                .contains(AuditAction.APPOINTMENT_BOOKED, AuditAction.SUMMARY_SAVED);
        AuditLog booked = audit.stream().filter(a -> a.getAction() == AuditAction.APPOINTMENT_BOOKED).findFirst().orElseThrow();
        assertThat(booked.getActorUserId()).isEqualTo(patient.id());
        assertThat(booked.getEntityId()).isEqualTo(id);
        // Clinical content stays out of the audit trail.
        assertThat(audit).allSatisfy(a -> assertThat(String.valueOf(a.getDetails())).doesNotContain("SECRET-DIAGNOSIS"));
    }

    @Test
    void summaryCannotBeWrittenForCanceledAppointment() throws Exception {
        TestUser doctor = doctor("Dr Cancel");
        TestUser patient = patient("Canceler");
        AppointmentSlot slot = slot(doctor, tomorrowAt(15));
        long id = body(mvc.perform(withJson(auth(post("/api/patient/appointments"), patient),
                Map.of("slotId", slot.getId()))).andReturn()).get("id").asLong();
        mvc.perform(auth(put("/api/patient/appointments/" + id + "/cancel"), patient)).andExpect(status().isOk());

        mvc.perform(withJson(auth(post("/api/doctor/appointments/" + id + "/summary"), doctor), Map.of("diagnosis", "x")))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(auth(put("/api/doctor/appointments/" + id + "/status"), doctor), Map.of("status", "BOOKED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanCreateDoctorsAndAdminsAndReadReportsAndAudit() throws Exception {
        TestUser admin = admin("Root");
        mvc.perform(withJson(auth(post("/api/admin/doctors"), admin), Map.of("fullName", "Dr New", "email", "drnew@test.local",
                        "password", PASSWORD, "specialization", "Cardiology", "department", "Heart")))
                .andExpect(status().isCreated());
        mvc.perform(withJson(auth(post("/api/admin/admins"), admin), Map.of("fullName", "Admin Two",
                        "email", "admin2@test.local", "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(auth(get("/api/admin/reports"), admin))
                .andExpect(jsonPath("$.doctors").value(1))
                .andExpect(jsonPath("$.byRole.ADMIN").value(2))
                .andExpect(jsonPath("$.byRole.DOCTOR").value(1));

        mvc.perform(auth(get("/api/admin/audit-logs?action=DOCTOR_CREATED"), admin))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].actorEmail").value(admin.user().getEmail()));
    }
}
