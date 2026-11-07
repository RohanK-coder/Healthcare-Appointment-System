package com.example.healthcare.booking;

import com.example.healthcare.entity.AppointmentSlot;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingConflictTest extends IntegrationTestBase {

    @Test
    void twentyConcurrentBookingsForOneSlot_exactlyOneSucceeds() throws Exception {
        TestUser doctor = doctor("Dr Race");
        AppointmentSlot slot = slot(doctor, tomorrowAt(10));
        int contenders = 20;
        List<TestUser> patients = new ArrayList<>();
        for (int i = 0; i < contenders; i++) patients.add(patient("Patient " + i));

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (TestUser patient : patients) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(withJson(auth(post("/api/patient/appointments"), patient),
                                Map.of("slotId", slot.getId(), "reason", "race")))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(contenders - 1);
        assertThat(appointmentRepository.countBySlotIdAndStatusNot(slot.getId(), AppointmentStatus.CANCELED)).isEqualTo(1);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void secondBookingOfSameSlotIsRejectedWithConflict() throws Exception {
        TestUser doctor = doctor("Dr One");
        AppointmentSlot slot = slot(doctor, tomorrowAt(9));
        TestUser alice = patient("Alice");
        TestUser bob = patient("Bob");

        mvc.perform(withJson(auth(post("/api/patient/appointments"), alice), Map.of("slotId", slot.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));
        mvc.perform(withJson(auth(post("/api/patient/appointments"), bob), Map.of("slotId", slot.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This slot is no longer available."));
    }

    @Test
    void canceledSlotCanBeRebookedByAnotherPatient() throws Exception {
        TestUser doctor = doctor("Dr Two");
        AppointmentSlot slot = slot(doctor, tomorrowAt(11));
        TestUser alice = patient("Alice");
        TestUser bob = patient("Bob");

        long appointmentId = bookAndGetId(alice, slot);
        mvc.perform(auth(put("/api/patient/appointments/" + appointmentId + "/cancel"), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        mvc.perform(withJson(auth(post("/api/patient/appointments"), bob), Map.of("slotId", slot.getId())))
                .andExpect(status().isOk());
        assertThat(appointmentRepository.countBySlotIdAndStatusNot(slot.getId(), AppointmentStatus.CANCELED)).isEqualTo(1);
    }

    @Test
    void cancelingTwiceDoesNotReleaseSomeoneElsesRebooking() throws Exception {
        TestUser doctor = doctor("Dr Three");
        AppointmentSlot slot = slot(doctor, tomorrowAt(12));
        TestUser alice = patient("Alice");
        TestUser bob = patient("Bob");
        TestUser carol = patient("Carol");

        long aliceAppointment = bookAndGetId(alice, slot);
        mvc.perform(auth(put("/api/patient/appointments/" + aliceAppointment + "/cancel"), alice)).andExpect(status().isOk());
        bookAndGetId(bob, slot);

        // Alice cancels her already-canceled appointment again; Bob's booking must survive.
        mvc.perform(auth(put("/api/patient/appointments/" + aliceAppointment + "/cancel"), alice))
                .andExpect(status().isBadRequest());
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        mvc.perform(withJson(auth(post("/api/patient/appointments"), carol), Map.of("slotId", slot.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void pastSlotCannotBeBooked() throws Exception {
        TestUser doctor = doctor("Dr Past");
        AppointmentSlot slot = slot(doctor, now().minusHours(2));
        mvc.perform(withJson(auth(post("/api/patient/appointments"), patient("Late")), Map.of("slotId", slot.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This slot is in the past."));
    }

    @Test
    void unknownSlotReturnsNotFound() throws Exception {
        mvc.perform(withJson(auth(post("/api/patient/appointments"), patient("Lost")), Map.of("slotId", 999_999)))
                .andExpect(status().isNotFound());
    }

    private long bookAndGetId(TestUser patient, AppointmentSlot slot) throws Exception {
        var result = mvc.perform(withJson(auth(post("/api/patient/appointments"), patient), Map.of("slotId", slot.getId())))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("id").asLong();
    }
}
