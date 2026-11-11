package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartLineRequest(@NotNull Long variantId, @Min(1) int quantity, Boolean selected) {}
