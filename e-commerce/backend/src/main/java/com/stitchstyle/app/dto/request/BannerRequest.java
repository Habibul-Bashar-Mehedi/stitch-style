package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BannerRequest(
        @NotBlank String title,
        @NotNull Long imageMediaId,
        String linkUrl,
        Integer displayOrder,
        Boolean active,
        Instant startsAt,
        Instant endsAt
) {}
