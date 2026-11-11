package com.stitchstyle.app.dto.response;

import java.time.Instant;

public record NoticeResponse(Long id, String title, String body, int priority, Instant startsAt, Instant endsAt) implements java.io.Serializable {}
