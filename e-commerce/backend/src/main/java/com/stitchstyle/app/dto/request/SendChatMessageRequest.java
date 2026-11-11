package com.stitchstyle.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank String clientMessageId,
        @NotBlank @Size(max = 4000) String body
) {
}
