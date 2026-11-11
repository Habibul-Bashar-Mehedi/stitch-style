package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;

public record VariantResponse(
        Long id,
        String sizeLabel,
        String sku,
        int stockQuantity,
        int lowStockThreshold,
        int reservedQuantity,
        int available,
        BigDecimal priceOverride,
        boolean active
) implements java.io.Serializable {}
