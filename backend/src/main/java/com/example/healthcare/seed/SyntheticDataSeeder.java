package com.example.healthcare.seed;

import com.example.healthcare.entity.*;
import com.example.healthcare.enums.AppointmentStatus;
import com.example.healthcare.enums.Role;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a deterministic synthetic dataset for the demo profile. Every name is fictional,
 * every email uses the reserved .test TLD, and clinical text is generic placeholder content.
 * Runs once: it is skipped when the first synthetic patient already exists.
 */
@Component
@Order(2)
@Profile("demo")
public class SyntheticDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SyntheticDataSeeder.class);
    private static final String MARKER_EMAIL = "patient001@patients.careslot.test";

    private static final String[] FIRST = {"Jordan", "Priya", "Mateo", "Hana", "Liam", "Amara", "Noah", "Sofia",
            "Ethan", "Zara", "Kenji", "Leila", "Omar", "Ines", "Tariq", "Mei", "Lucas", "Ana", "Ravi", "Chloe"};
    private static final String[] LAST = {"Okafor", "Nguyen", "Silva", "Kowalski", "Haddad", "Larsen", "Mendes",
            "Tanaka", "Brennan", "Osei", "Varga", "Dubois", "Rahman", "Moreau", "Ibarra", "Lindqvist"};
    private static final String[][] SPECIALTIES = {
            {"General Medicine", "Primary Care"}, {"Cardiology", "Heart Center"}, {"Dermatology", "Outpatient"},
            {"Pediatrics", "Children's Clinic"}, {"Orthopedics", "Musculoskeletal"}, {"Neurology", "Neurosciences"},
            {"Endocrinology", "Internal Medicine"}, {"Ophthalmology", "Eye Clinic"}};
    private static final String[] REASONS = {"Routine checkup", "Follow-up visit", "Medication review",
            "Annual physical", "Lab results discussion", "New symptom consultation"};

    private final AppUserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSummaryRepository summaryRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int doctorCount;
    private final int patientCount;
    private final int daysAhead;
    private final int daysHistory;

    public SyntheticDataSeeder(AppUserRepository userRepository, DoctorRepository doctorRepository,
                               PatientRepository patientRepository, AppointmentSlotRepository slotRepository,
                               AppointmentRepository appointmentRepository, AppointmentSummaryRepository summaryRepository,
                               PasswordEncoder passwordEncoder, Clock clock,
                               @Value("${app.seed.synthetic.doctors:12}") int doctorCount,
                               @Value("${app.seed.synthetic.patients:200}") int patientCount,
                               @Value("${app.seed.synthetic.days-ahead:14}") int daysAhead,
                               @Value("${app.seed.synthetic.days-history:30}") int daysHistory) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.summaryRepository = summaryRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.doctorCount = doctorCount;
        this.patientCount = patientCount;
        this.daysAhead = daysAhead;
        this.daysHistory = daysHistory;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail(MARKER_EMAIL)) {
            log.info("Synthetic dataset already present; skipping generation.");
            return;
        }
        long started = System.currentTimeMillis();
        Random random = new Random(42);
        // One hash shared by all synthetic accounts: hashing hundreds of passwords at cost 12 takes minutes.
        String hash = passwordEncoder.encode(DataSeeder.DEMO_PASSWORD);

        List<Doctor> doctors = new ArrayList<>();
        for (int i = 1; i <= doctorCount; i++) {
            String first = FIRST[random.nextInt(FIRST.length)];
            String last = LAST[random.nextInt(LAST.length)];
            AppUser user = newUser(first + " " + last, String.format("doctor%02d@doctors.careslot.test", i), Role.DOCTOR, hash);
            String[] specialty = SPECIALTIES[(i - 1) % SPECIALTIES.length];
            Doctor doctor = new Doctor();
            doctor.setUser(user);
            doctor.setSpecialization(specialty[0]);
            doctor.setDepartment(specialty[1]);
            doctor.setBio("Synthetic " + specialty[0].toLowerCase() + " profile generated for the demo.");
            doctors.add(doctorRepository.save(doctor));
        }

        List<Patient> patients = new ArrayList<>();
        for (int i = 1; i <= patientCount; i++) {
            String first = FIRST[random.nextInt(FIRST.length)];
            String last = LAST[random.nextInt(LAST.length)];
            AppUser user = newUser(first + " " + last, String.format("patient%03d@patients.careslot.test", i), Role.PATIENT, hash);
            Patient patient = new Patient();
            patient.setUser(user);
            patient.setPhone(String.format("555-%04d", i));
            patient.setDateOfBirth(LocalDate.of(1950 + random.nextInt(55), 1 + random.nextInt(12), 1 + random.nextInt(28)));
            patients.add(patientRepository.save(patient));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int slots = 0, appointments = 0;
        for (Doctor doctor : doctors) {
            for (LocalDate day = today.minusDays(daysHistory); !day.isAfter(today.plusDays(daysAhead)); day = day.plusDays(1)) {
                if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                for (LocalTime time = LocalTime.of(9, 0); time.isBefore(LocalTime.of(16, 0)); time = time.plusMinutes(30)) {
                    if (time.equals(LocalTime.of(12, 0)) || time.equals(LocalTime.of(12, 30))) continue; // lunch
                    LocalDateTime start = LocalDateTime.of(day, time);
                    boolean past = !start.isAfter(now);
                    // Past slots only exist where history happened; future slots are ~35% booked.
                    boolean booked = past ? random.nextDouble() < 0.6 : random.nextDouble() < 0.35;
                    if (past && !booked) continue;

                    AppointmentSlot slot = new AppointmentSlot();
                    slot.setDoctor(doctor);
                    slot.setSlotStart(start);
                    slot.setSlotEnd(start.plusMinutes(30));
                    slot.setStatus(booked ? SlotStatus.BOOKED : SlotStatus.AVAILABLE);
                    slot = slotRepository.save(slot);
                    slots++;
                    if (!booked) continue;

                    Appointment appointment = new Appointment();
                    appointment.setPatient(patients.get(random.nextInt(patients.size())));
                    appointment.setDoctor(doctor);
                    appointment.setSlot(slot);
                    appointment.setReason(REASONS[random.nextInt(REASONS.length)]);
                    appointment.setStatus(past ? pastOutcome(random) : AppointmentStatus.BOOKED);
                    appointment.setReminderSent(past);
                    appointment = appointmentRepository.save(appointment);
                    appointments++;
                    if (appointment.getStatus() == AppointmentStatus.CANCELED) {
                        slot.setStatus(SlotStatus.AVAILABLE);
                    } else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
                        AppointmentSummary summary = new AppointmentSummary();
                        summary.setAppointment(appointment);
                        summary.setDiagnosis("Synthetic visit note: no significant findings");
                        summary.setPrescription("None (synthetic data)");
                        summary.setDoctorNotes("Placeholder summary generated for the demo dataset.");
                        summaryRepository.save(summary);
                    }
                }
            }
        }
        log.info("Generated synthetic dataset: {} doctors, {} patients, {} slots, {} appointments in {} ms",
                doctors.size(), patients.size(), slots, appointments, System.currentTimeMillis() - started);
    }

    private static AppointmentStatus pastOutcome(Random random) {
        double r = random.nextDouble();
        if (r < 0.80) return AppointmentStatus.COMPLETED;
        if (r < 0.92) return AppointmentStatus.CANCELED;
        return AppointmentStatus.NO_SHOW;
    }

    private AppUser newUser(String fullName, String email, Role role, String hash) {
        AppUser user = new AppUser();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setRole(role);
        return userRepository.save(user);
    }
}
