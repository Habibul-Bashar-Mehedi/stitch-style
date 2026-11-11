package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusRequest(@NotNull OrderStatus status, String reason) {}
