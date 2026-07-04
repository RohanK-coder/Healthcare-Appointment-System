package com.example.healthcare.service;

import com.example.healthcare.entity.AppUser;
import com.example.healthcare.exception.ApiException;
import com.example.healthcare.repository.AppUserRepository;
import com.example.healthcare.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw ApiException.forbidden("Authentication is required.");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Authenticated user was not found."));
    }
}
