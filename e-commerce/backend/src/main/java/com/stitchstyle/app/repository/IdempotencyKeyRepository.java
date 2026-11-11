package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByScopeKeyAndIdempotencyKey(String scopeKey, String idempotencyKey);
    long deleteByExpiresAtBefore(Instant expiresAt);
}
