package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SettingRequest(@NotBlank String key, String value, String valueType) {}
