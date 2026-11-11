package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.model.entity.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MyOrderResponse(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal grandTotal,
        Instant placedAt,
        Instant shippedAt,
        Instant deliveredAt,
        List<TrackingResponse.TrackingItem> items,
        ReturnSummary returnInfo
) {
    public enum Eligibility { ELIGIBLE, NOT_DELIVERED, WINDOW_CLOSED, ALREADY_REQUESTED }

    public record ReturnSummary(
            Long returnId,
            ReturnStatus status,
            Instant requestedAt,
            Instant resolvedAt,
            Instant windowEndsAt,
            Eligibility eligibility,
            String ineligibleReason,
            Long previousReturnId,
            String previousRejectionReason
    ) {
    }
}
