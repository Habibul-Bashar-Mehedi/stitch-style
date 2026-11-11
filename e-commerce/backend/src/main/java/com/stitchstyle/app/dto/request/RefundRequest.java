package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record RefundRequest(@NotBlank String method, @DecimalMin("0.01") BigDecimal amount) {}
