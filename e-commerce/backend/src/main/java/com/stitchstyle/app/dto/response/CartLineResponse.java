package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;

public record CartLineResponse(
        Long variantId,
        Long productId,
        String productSlug,
        String productName,
        String sizeLabel,
        int quantity,
        boolean selected,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        int available,
        boolean priceChanged,
        boolean stockChanged
) {}
