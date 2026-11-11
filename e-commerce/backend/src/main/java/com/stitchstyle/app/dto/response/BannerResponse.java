package com.stitchstyle.app.dto.response;

import java.time.Instant;

public record BannerResponse(Long id, String title, String imageUrl, String linkUrl, int displayOrder, Instant startsAt, Instant endsAt) implements java.io.Serializable {}
