package com.stitchstyle.app.dto.request;

import com.stitchstyle.app.model.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewModerationRequest(@NotNull ReviewStatus status) {}
