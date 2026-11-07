package com.example.healthcare.repository;

import com.example.healthcare.entity.AppUser;
import com.example.healthcare.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<AppUser> findAllByOrderByCreatedAtDesc();

    interface RoleCount {
        Role getRole();
        long getTotal();
    }

    @Query("select u.role as role, count(u) as total from AppUser u group by u.role")
    List<RoleCount> countByRole();
}
