package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TrackingRequest(@NotBlank String orderNumber, @NotBlank String phone) {}
