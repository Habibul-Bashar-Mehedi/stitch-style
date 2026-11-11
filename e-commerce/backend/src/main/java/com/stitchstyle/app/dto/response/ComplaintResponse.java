package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.ComplaintStatus;
import java.time.Instant;

public record ComplaintResponse(Long id, String name, String phone, String orderNumber, String message,
                                ComplaintStatus status, Instant dueAt, Instant respondedAt, String response,
                                Instant createdAt, boolean overdue) {}
