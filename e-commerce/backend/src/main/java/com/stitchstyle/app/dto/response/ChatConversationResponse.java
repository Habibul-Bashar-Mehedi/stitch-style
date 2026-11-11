package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.ChatConversationStatus;
import com.stitchstyle.app.model.entity.ChatConversationType;

import java.time.Instant;

public record ChatConversationResponse(
        Long id,
        ChatConversationType type,
        ChatConversationStatus status,
        ChatUserResponse counterpart,
        ChatUserResponse assignedStaff,
        String orderNumber,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unreadCount,
        boolean assignedToMe
) {
}
