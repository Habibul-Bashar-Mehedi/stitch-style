package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record VariantRequest(
        Long id,
        @NotBlank String sizeLabel,
        @NotBlank String sku,
        @Min(0) int stockQuantity,
        Integer lowStockThreshold,
        BigDecimal priceOverride,
        Boolean active
) {}
