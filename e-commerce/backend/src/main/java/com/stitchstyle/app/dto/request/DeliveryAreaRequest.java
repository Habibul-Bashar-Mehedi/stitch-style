package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record DeliveryAreaRequest(
        @NotBlank String name,
        String district,
        @DecimalMin("0.00") BigDecimal deliveryCharge,
        BigDecimal freeShippingThreshold,
        Integer slaDays,
        Boolean active,
        Integer displayOrder
) {}
