package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String customerPhone,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal grandTotal,
        Instant placedAt
) {}
