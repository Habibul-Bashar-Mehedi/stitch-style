package com.stitchstyle.app.chat;

import com.stitchstyle.app.model.entity.ChatConversation;
import com.stitchstyle.app.model.entity.NotificationChannel;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatUnreadNotifier {

    private static final Duration DEBOUNCE = Duration.ofMinutes(15);
    private static final int PREVIEW_LENGTH = 140;

    private final NotificationService notificationService;
    private final ChatPresenceRegistry presence;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    public ChatUnreadNotifier(NotificationService notificationService, ChatPresenceRegistry presence) {
        this.notificationService = notificationService;
        this.presence = presence;
    }

    public void notifyOffline(List<User> recipients, ChatConversation conversation, User sender, String body) {
        String preview = body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) : body;
        for (User recipient : recipients) {
            if (recipient.getEmail() == null || recipient.getEmail().isBlank()) continue;
            if (presence.isOnline(recipient.getId())) continue;
            String key = conversation.getId() + ":" + recipient.getId();
            Instant now = Instant.now();
            Instant previous = lastSent.get(key);
            if (previous != null && previous.plus(DEBOUNCE).isAfter(now)) continue;
            lastSent.put(key, now);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("conversationId", conversation.getId());
            payload.put("senderName", sender.getFullName());
            payload.put("preview", preview);
            notificationService.enqueue(null, NotificationChannel.EMAIL, recipient.getEmail(),
                    "CHAT_MESSAGE_UNREAD", payload);
        }
    }
}
