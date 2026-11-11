package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(
        @NotNull @Valid CheckoutCustomer customer,
        @NotBlank String shippingAddress,
        @NotNull Long deliveryAreaId,
        @NotNull PaymentMethod paymentMethod,
        String note,
        String couponCode,
        @NotEmpty List<@Valid CartLineRequest> items
) {}
