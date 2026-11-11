package com.stitchstyle.app.dto.response;

import java.time.Instant;

public record MediaResponse(
        Long id,
        String url,
        String originalFilename,
        String mimeType,
        long byteSize,
        Integer width,
        Integer height,
        Instant createdAt
) {}
