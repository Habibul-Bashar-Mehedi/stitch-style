package com.stitchstyle.app.chat;

import com.stitchstyle.app.dto.response.ChatMessageResponse;

import java.util.UUID;

public record ChatEvent(
        String eventId,
        String type,
        Long conversationId,
        ChatMessageResponse message,
        Long readerId,
        Long lastReadMessageId,
        String change,
        String typingUser
) {
    public static ChatEvent message(Long conversationId, ChatMessageResponse message) {
        return new ChatEvent(UUID.randomUUID().toString(), "MESSAGE_CREATED", conversationId, message,
                null, null, null, null);
    }

    public static ChatEvent read(Long conversationId, Long readerId, Long lastReadMessageId) {
        return new ChatEvent(UUID.randomUUID().toString(), "MESSAGE_READ", conversationId, null,
                readerId, lastReadMessageId, null, null);
    }

    public static ChatEvent conversation(Long conversationId, String change) {
        return new ChatEvent(UUID.randomUUID().toString(), "CONVERSATION_UPDATED", conversationId, null,
                null, null, change, null);
    }

    public static ChatEvent typing(Long conversationId, String name) {
        return new ChatEvent(UUID.randomUUID().toString(), "TYPING", conversationId, null,
                null, null, null, name);
    }
}
