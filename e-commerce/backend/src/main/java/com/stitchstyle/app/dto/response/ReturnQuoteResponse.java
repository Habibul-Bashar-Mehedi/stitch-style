package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ReturnQuoteResponse(
        BigDecimal itemsAmount,
        BigDecimal couponDiscountShare,
        BigDecimal deliveryAmount,
        BigDecimal total,
        boolean fullOrder,
        String currency,
        Instant windowEndsAt
) {
}
