package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInternalConversationRequest(
        @NotNull Long recipientUserId,
        @NotBlank String clientMessageId,
        @NotBlank @Size(max = 4000) String body
) {
}
