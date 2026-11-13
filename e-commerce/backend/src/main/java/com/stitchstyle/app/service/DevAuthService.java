package com.stitchstyle.app.service;

import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Resolves (or creates) the user for the test-only sign-in endpoint. Lives in
 * the service layer so controllers never touch repositories (ArchUnit rule).
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevAuthService {

    private final UserRepository userRepository;

    public DevAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User signIn(String email, String role, String fullName) {
        String resolvedEmail = email == null || email.isBlank()
                ? "e2e-admin@stitchandstyle.test"
                : email.trim();
        Role resolvedRole = parseRole(role);
        String resolvedName = fullName == null || fullName.isBlank()
                ? "E2E " + resolvedRole.name()
                : fullName.trim();

        User user = userRepository.findByEmailIgnoreCase(resolvedEmail).orElseGet(() -> userRepository.save(User.builder()
                .email(resolvedEmail)
                .fullName(resolvedName)
                .role(resolvedRole)
                .active(true)
                .build()));
        if (user.getRole() != resolvedRole || !user.isActive()) {
            user.setRole(resolvedRole);
            user.setActive(true);
            user = userRepository.save(user);
        }
        return user;
    }

    private static Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return Role.ADMIN;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + value);
        }
    }
}
