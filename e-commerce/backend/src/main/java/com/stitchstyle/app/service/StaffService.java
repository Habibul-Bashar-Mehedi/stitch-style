package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.StaffRequest;
import com.stitchstyle.app.dto.response.StaffResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class StaffService {

    private final UserRepository userRepository;
    private final SessionInvalidationService sessionInvalidationService;
    private final AuditService auditService;

    public StaffService(UserRepository userRepository, SessionInvalidationService sessionInvalidationService,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.sessionInvalidationService = sessionInvalidationService;
        this.auditService = auditService;
    }

    public List<StaffResponse> list() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StaffResponse create(StaffRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(User::new);
        if (user.getId() != null && (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER)) {
            throw new DuplicateResourceException("A staff account already exists for " + email + ".");
        }
        user.setEmail(email);
        user.setFullName(request.fullName() == null || request.fullName().isBlank() ? email : request.fullName());
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());
        userRepository.save(user);
        auditService.record(com.stitchstyle.app.model.entity.AuditAction.CREATE, "STAFF", user.getId(), null,
                java.util.Map.of("email", email, "role", request.role().name()));
        return toResponse(user);
    }

    @Transactional
    public StaffResponse update(Long id, StaffRequest request) {
        User user = getRequired(id);
        Role previousRole = user.getRole();
        boolean previousActive = user.isActive();
        if (previousRole == Role.ADMIN && (request.role() != Role.ADMIN || Boolean.FALSE.equals(request.active()))) {
            ensureAnotherAdmin(user);
        }
        user.setFullName(request.fullName() == null || request.fullName().isBlank() ? user.getFullName() : request.fullName());
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());
        userRepository.save(user);
        if (previousRole != user.getRole() || previousActive != user.isActive()) {
            sessionInvalidationService.invalidate(user.getEmail());
        }
        auditService.record(com.stitchstyle.app.model.entity.AuditAction.UPDATE, "STAFF", user.getId(),
                java.util.Map.of("role", previousRole.name(), "active", previousActive),
                java.util.Map.of("role", user.getRole().name(), "active", user.isActive()));
        return toResponse(user);
    }

    @Transactional
    public StaffResponse setActive(Long id, boolean active) {
        User user = getRequired(id);
        if (!active && user.getRole() == Role.ADMIN) ensureAnotherAdmin(user);
        user.setActive(active);
        userRepository.save(user);
        if (!active) sessionInvalidationService.invalidate(user.getEmail());
        auditService.record(com.stitchstyle.app.model.entity.AuditAction.UPDATE, "STAFF", user.getId(),
                java.util.Map.of("active", !active), java.util.Map.of("active", active));
        return toResponse(user);
    }

    private void ensureAnotherAdmin(User current) {
        if (userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new InvalidStatusTransitionException("The last active Admin cannot be disabled or demoted.");
        }
    }

    private User getRequired(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Staff account not found: " + id));
    }

    private StaffResponse toResponse(User user) {
        return new StaffResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.isActive(), user.getLastLoginAt());
    }
}
