package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ComplaintRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^(?:\\+8801[3-9]\\d{8}|01[3-9]\\d{8})$") String phone,
        String orderNumber,
        @NotBlank String message
) {}
