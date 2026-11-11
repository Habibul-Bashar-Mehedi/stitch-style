package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.SizeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProductResponse(
        Long id,
        Long categoryId,
        String categorySlug,
        Map<String, String> nameI18n,
        String slug,
        Map<String, String> descriptionI18n,
        String name,
        String description,
        BigDecimal basePrice,
        BigDecimal discountPrice,
        BigDecimal effectivePrice,
        SizeType sizeType,
        String originCountry,
        String brand,
        String model,
        String deliveryEstimate,
        boolean active,
        boolean featured,
        List<VariantResponse> variants,
        List<ProductImageResponse> images,
        Instant createdAt
) implements java.io.Serializable {}
