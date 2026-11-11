package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(@NotNull Long lastReadMessageId) {
}
