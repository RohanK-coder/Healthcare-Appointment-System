package com.example.healthcare.seed;

import com.example.healthcare.entity.*;
import com.example.healthcare.enums.Role;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * Seeds one well-known account per role so the portals can be tried immediately.
 * On every start the demo passwords and lockout state are reset, so a visitor to the public
 * demo cannot lock others out by changing or brute-forcing these accounts.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.seed.demo-accounts", havingValue = "true")
public class DataSeeder implements CommandLineRunner {
    public static final String DEMO_PASSWORD = "DemoPass123";
    public static final String ADMIN_EMAIL = "admin@careslot.test";
    public static final String DOCTOR_EMAIL = "doctor@careslot.test";
    public static final String PATIENT_EMAIL = "patient@careslot.test";
    public static final Set<String> DEMO_EMAILS = Set.of(ADMIN_EMAIL, DOCTOR_EMAIL, PATIENT_EMAIL);

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentSlotRepository slotRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DataSeeder(AppUserRepository userRepository,
                      DoctorRepository doctorRepository,
                      PatientRepository patientRepository,
                      AppointmentSlotRepository slotRepository,
                      PasswordEncoder passwordEncoder,
                      Clock clock) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.slotRepository = slotRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        upsertDemoUser("Avery Admin (demo)", ADMIN_EMAIL, Role.ADMIN, hash);
        AppUser doctorUser = upsertDemoUser("Maya Carter (demo)", DOCTOR_EMAIL, Role.DOCTOR, hash);
        AppUser patientUser = upsertDemoUser("Alex Rivera (demo)", PATIENT_EMAIL, Role.PATIENT, hash);

        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId()).orElseGet(() -> {
            Doctor d = new Doctor();
            d.setUser(doctorUser);
            d.setSpecialization("General Medicine");
            d.setDepartment("Primary Care");
            d.setBio("Synthetic demo physician available for routine consultations.");
            return doctorRepository.save(d);
        });

        patientRepository.findByUserId(patientUser.getId()).orElseGet(() -> {
            Patient p = new Patient();
            p.setUser(patientUser);
            p.setPhone("555-0100");
            p.setDateOfBirth(LocalDate.of(1990, 1, 15));
            return patientRepository.save(p);
        });

        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        for (int i = 0; i < 8; i++) {
            LocalDateTime start = LocalDateTime.of(tomorrow, LocalTime.of(9, 0).plusMinutes(i * 30L));
            if (!slotRepository.existsByDoctorIdAndSlotStart(doctor.getId(), start)) {
                AppointmentSlot slot = new AppointmentSlot();
                slot.setDoctor(doctor);
                slot.setSlotStart(start);
                slot.setSlotEnd(start.plusMinutes(30));
                slot.setStatus(SlotStatus.AVAILABLE);
                slotRepository.save(slot);
            }
        }

        log.info("Demo accounts ready: {}, {}, {} (password: {})", ADMIN_EMAIL, DOCTOR_EMAIL, PATIENT_EMAIL, DEMO_PASSWORD);
    }

    private AppUser upsertDemoUser(String fullName, String email, Role role, String passwordHash) {
        AppUser user = userRepository.findByEmail(email).orElseGet(AppUser::new);
        if (user.getId() == null) {
            user.setFullName(fullName);
            user.setEmail(email);
            user.setRole(role);
        }
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return userRepository.save(user);
    }
}
