package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.AuditAction;
import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(Long id, String actorEmail, AuditAction action, String entityType, Long entityId,
                               Map<String, Object> beforeState, Map<String, Object> afterState,
                               String ipAddress, Instant createdAt) {}
