package com.example.healthcare.seed;

import com.example.healthcare.entity.*;
import com.example.healthcare.enums.Role;
import com.example.healthcare.enums.SlotStatus;
import com.example.healthcare.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentSlotRepository slotRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AppUserRepository userRepository,
                      DoctorRepository doctorRepository,
                      PatientRepository patientRepository,
                      AppointmentSlotRepository slotRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.slotRepository = slotRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppUser admin = createUserIfMissing("Admin User", "admin@healthcare.local", Role.ADMIN);
        AppUser doctorUser = createUserIfMissing("Maya Carter", "doctor@healthcare.local", Role.DOCTOR);
        AppUser patientUser = createUserIfMissing("Alex Patient", "patient@healthcare.local", Role.PATIENT);

        Doctor doctor = doctorRepository.findByUserId(doctorUser.getId()).orElseGet(() -> {
            Doctor d = new Doctor();
            d.setUser(doctorUser);
            d.setSpecialization("General Medicine");
            d.setDepartment("Primary Care");
            d.setBio("Board-certified physician available for routine consultations.");
            return doctorRepository.save(d);
        });

        patientRepository.findByUserId(patientUser.getId()).orElseGet(() -> {
            Patient p = new Patient();
            p.setUser(patientUser);
            p.setPhone("555-0100");
            p.setDateOfBirth(LocalDate.of(1998, 1, 15));
            return patientRepository.save(p);
        });

        LocalDate tomorrow = LocalDate.now().plusDays(1);
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

        log.info("Demo users ready. admin@healthcare.local, doctor@healthcare.local, patient@healthcare.local all use password: password");
    }

    private AppUser createUserIfMissing(String fullName, String email, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("password"));
            user.setRole(role);
            user.setActive(true);
            return userRepository.save(user);
        });
    }
}
