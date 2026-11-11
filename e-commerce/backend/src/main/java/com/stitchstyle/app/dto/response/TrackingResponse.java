package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TrackingResponse(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal grandTotal,
        Instant placedAt,
        Instant shippedAt,
        Instant deliveredAt,
        List<TrackingItem> items
) {
    public record TrackingItem(Long orderItemId, String productName, String sizeLabel, int quantity, BigDecimal unitPrice) {}
}
