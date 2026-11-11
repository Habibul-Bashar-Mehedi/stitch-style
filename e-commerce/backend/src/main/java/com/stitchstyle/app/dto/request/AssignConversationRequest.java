package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignConversationRequest(@NotNull Long staffUserId) {
}
