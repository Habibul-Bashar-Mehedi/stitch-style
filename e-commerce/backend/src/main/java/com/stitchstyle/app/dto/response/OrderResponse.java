package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentStatus;
import java.math.BigDecimal;

public record OrderResponse(
        String orderNumber,
        String publicToken,
        OrderStatus status,
        PaymentStatus paymentStatus,
        String currency,
        BigDecimal subtotal,
        BigDecimal deliveryCharge,
        BigDecimal discountTotal,
        BigDecimal grandTotal,
        String trackingUrl,
        String redirectUrl,
        boolean replayed
) {}
