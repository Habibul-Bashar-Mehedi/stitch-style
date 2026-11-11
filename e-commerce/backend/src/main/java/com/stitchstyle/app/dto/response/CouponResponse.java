package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(Long id, String code, String discountType, BigDecimal discountValue,
                             BigDecimal minSubtotal, Integer maxUses, int usedCount,
                             Instant startsAt, Instant endsAt, boolean active) {}
