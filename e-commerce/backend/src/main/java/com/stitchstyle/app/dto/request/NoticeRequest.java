package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record NoticeRequest(
        @NotBlank String title,
        @NotBlank String body,
        Integer priority,
        Boolean active,
        Instant startsAt,
        Instant endsAt
) {}
