package com.stitchstyle.app.dto.response;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        ChatUserResponse sender,
        String body,
        String clientMessageId,
        Instant createdAt,
        boolean readByOthers
) {
}
