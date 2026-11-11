package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.AuditLogResponse;
import com.stitchstyle.app.dto.response.PageResponse;
import com.stitchstyle.app.model.entity.AuditAction;
import com.stitchstyle.app.model.entity.AuditLog;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.AuditLogRepository;
import com.stitchstyle.app.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(AuditAction action, String entityType, Long entityId,
                       Map<String, Object> before, Map<String, Object> after) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())
                ? authentication.getName() : null;
        User actor = email == null ? null : userRepository.findByEmailIgnoreCase(email).orElse(null);
        AuditLog log = AuditLog.builder()
                .actorUser(actor)
                .actorEmail(email)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .beforeState(before)
                .afterState(after)
                .build();
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String actorEmail, String entityType, Instant from, Instant to,
                                                  int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorEmail != null && !actorEmail.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), "%" + actorEmail.toLowerCase() + "%"));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.toUpperCase()));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> result = repository.findAll(spec, pageable);
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorEmail(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getBeforeState(), log.getAfterState(), log.getIpAddress(), log.getCreatedAt());
    }
}
