package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CouponRequest(
        @NotBlank String code,
        @NotBlank String discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        BigDecimal minSubtotal,
        Integer maxUses,
        Instant startsAt,
        Instant endsAt,
        Boolean active
) {}
