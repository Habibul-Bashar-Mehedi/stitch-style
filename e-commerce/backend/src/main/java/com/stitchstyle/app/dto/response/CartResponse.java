package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartLineResponse> lines,
        BigDecimal subtotal,
        BigDecimal deliveryCharge,
        BigDecimal discountTotal,
        BigDecimal grandTotal
) {}
