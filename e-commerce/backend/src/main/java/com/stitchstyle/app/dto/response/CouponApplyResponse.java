package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;

public record CouponApplyResponse(String code, BigDecimal discount, String message) {}
