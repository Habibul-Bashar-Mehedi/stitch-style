package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ComplaintResponseRequest(@NotBlank String response, Boolean close) {}
