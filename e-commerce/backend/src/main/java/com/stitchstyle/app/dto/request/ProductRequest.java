package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.SizeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductRequest(
        @NotNull Long categoryId,
        @NotEmpty Map<String, String> nameI18n,
        @Size(max = 255) String slug,
        Map<String, String> descriptionI18n,
        @NotNull @DecimalMin("0.00") BigDecimal basePrice,
        @DecimalMin("0.00") BigDecimal discountPrice,
        SizeType sizeType,
        String originCountry,
        String brand,
        String model,
        String deliveryEstimate,
        Boolean active,
        Boolean featured,
        List<@Valid VariantRequest> variants,
        List<Long> imageMediaIds
) {}
