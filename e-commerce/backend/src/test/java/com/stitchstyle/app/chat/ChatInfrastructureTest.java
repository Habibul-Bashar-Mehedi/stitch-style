package com.stitchstyle.app.chat;

import com.stitchstyle.app.dto.response.ChatMessageResponse;
import com.stitchstyle.app.model.entity.ChatConversation;
import com.stitchstyle.app.model.entity.ChatConversationStatus;
import com.stitchstyle.app.model.entity.ChatConversationType;
import com.stitchstyle.app.model.entity.NotificationChannel;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.service.ChatService;
import com.stitchstyle.app.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatInfrastructureTest {

    @Test
    void presenceTracksMultipleSessionsAndRemovesOnLastDisconnect() {
        ChatPresenceRegistry registry = new ChatPresenceRegistry();

        registry.online(1L);
        registry.online(1L);
        registry.online(2L);

        assertThat(registry.isOnline(1L)).isTrue();
        assertThat(registry.onlineCount()).isEqualTo(2);

        registry.offline(1L);
        assertThat(registry.isOnline(1L)).isTrue();
        registry.offline(1L);
        assertThat(registry.isOnline(1L)).isFalse();
        registry.offline(999L);
        assertThat(registry.onlineCount()).isEqualTo(1);
    }

    @Test
    void eventFactoriesPopulateTypeAndPayload() {
        ChatMessageResponse message = new ChatMessageResponse(1L, 2L, null, "body", "client",
                Instant.now(), false);

        ChatEvent created = ChatEvent.message(2L, message);
        assertThat(created.type()).isEqualTo("MESSAGE_CREATED");
        assertThat(created.eventId()).isNotBlank();
        assertThat(created.message()).isSameAs(message);

        assertThat(ChatEvent.read(2L, 3L, 4L).type()).isEqualTo("MESSAGE_READ");
        assertThat(ChatEvent.read(2L, 3L, 4L).lastReadMessageId()).isEqualTo(4L);
        assertThat(ChatEvent.conversation(2L, "CLOSED").change()).isEqualTo("CLOSED");
        assertThat(ChatEvent.typing(2L, "Nusrat").typingUser()).isEqualTo("Nusrat");
    }

    @Test
    void unreadNotifierSkipsOnlineAndBlankEmailRecipients() {
        NotificationService notificationService = mock(NotificationService.class);
        ChatPresenceRegistry presence = new ChatPresenceRegistry();
        ChatUnreadNotifier notifier = new ChatUnreadNotifier(notificationService, presence);
        ChatConversation conversation = ChatConversation.builder().id(5L)
                .type(ChatConversationType.SUPPORT).status(ChatConversationStatus.OPEN).build();
        User sender = user(1L, "Sender", "sender@x.com");
        User offline = user(2L, "Offline", "offline@x.com");
        User online = user(3L, "Online", "online@x.com");
        User blank = user(4L, "Blank", " ");
        presence.online(3L);

        notifier.notifyOffline(List.of(offline, online, blank), conversation, sender, "Hello");

        verify(notificationService).enqueue(eq(null), eq(NotificationChannel.EMAIL), eq("offline@x.com"),
                eq("CHAT_MESSAGE_UNREAD"), any());
        verify(notificationService, never()).enqueue(any(), any(), eq("online@x.com"), any(), any());
        verify(notificationService, never()).enqueue(any(), any(), eq(" "), any(), any());
    }

    @Test
    void unreadNotifierDebouncesWithinTheWindow() {
        NotificationService notificationService = mock(NotificationService.class);
        ChatUnreadNotifier notifier = new ChatUnreadNotifier(notificationService, new ChatPresenceRegistry());
        ChatConversation conversation = ChatConversation.builder().id(5L)
                .type(ChatConversationType.SUPPORT).status(ChatConversationStatus.OPEN).build();
        User sender = user(1L, "Sender", "sender@x.com");
        User recipient = user(2L, "Recipient", "recipient@x.com");

        notifier.notifyOffline(List.of(recipient), conversation, sender, "First");
        notifier.notifyOffline(List.of(recipient), conversation, sender, "Second");

        verify(notificationService, org.mockito.Mockito.times(1))
                .enqueue(eq(null), eq(NotificationChannel.EMAIL), eq("recipient@x.com"),
                        eq("CHAT_MESSAGE_UNREAD"), any());
    }

    @Test
    void unreadNotifierTruncatesPreviewTo140Characters() {
        NotificationService notificationService = mock(NotificationService.class);
        ChatUnreadNotifier notifier = new ChatUnreadNotifier(notificationService, new ChatPresenceRegistry());
        ChatConversation conversation = ChatConversation.builder().id(5L).build();
        User recipient = user(2L, "Recipient", "recipient@x.com");

        notifier.notifyOffline(List.of(recipient), conversation, user(1L, "S", "s@x.com"), "x".repeat(500));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(notificationService).enqueue(any(), any(), any(), any(), captor.capture());
        assertThat((String) captor.getValue().get("preview")).hasSize(140);
    }

    @Test
    void realtimeListenerMapsDomainEventsOntoTheBus() {
        ChatEventBus bus = mock(ChatEventBus.class);
        ChatRealtimeListener listener = new ChatRealtimeListener(bus);

        listener.onMessage(new ChatService.ChatMessageCreatedEvent(1L,
                new ChatMessageResponse(2L, 1L, null, "body", "client", Instant.now(), false), List.of(9L)));
        verify(bus).publish(eq(List.of(9L)), org.mockito.ArgumentMatchers.argThat(
                event -> "MESSAGE_CREATED".equals(event.type()) && event.conversationId().equals(1L)));

        listener.onRead(new ChatService.ChatReadEvent(1L, 3L, 4L, List.of(9L)));
        verify(bus).publish(eq(List.of(9L)), org.mockito.ArgumentMatchers.argThat(
                event -> "MESSAGE_READ".equals(event.type())));

        listener.onConversation(new ChatService.ChatConversationChangedEvent(1L, List.of(9L), "CLOSED"));
        verify(bus).publish(eq(List.of(9L)), org.mockito.ArgumentMatchers.argThat(
                event -> "CONVERSATION_UPDATED".equals(event.type()) && "CLOSED".equals(event.change())));

        listener.onTyping(new ChatService.ChatTypingEvent(1L, "Nusrat", List.of(9L)));
        verify(bus).publish(eq(List.of(9L)), org.mockito.ArgumentMatchers.argThat(
                event -> "TYPING".equals(event.type()) && "Nusrat".equals(event.typingUser())));
    }

    @Test
    void realtimeDeliverySendsToEachRecipientsEmail() {
        org.springframework.messaging.simp.SimpMessagingTemplate template =
                mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);
        com.stitchstyle.app.repository.UserRepository userRepository =
                mock(com.stitchstyle.app.repository.UserRepository.class);
        ChatRealtimeDelivery delivery = new ChatRealtimeDelivery(template, userRepository);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "A", "a@x.com")));
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(user(2L, "B", "b@x.com")));
        when(userRepository.findById(3L)).thenReturn(java.util.Optional.empty());

        delivery.deliver(List.of(1L, 2L, 3L), ChatEvent.conversation(9L, "CLOSED"));

        verify(template).convertAndSendToUser(eq("a@x.com"), eq("/queue/chat"), any(ChatEvent.class));
        verify(template).convertAndSendToUser(eq("b@x.com"), eq("/queue/chat"), any(ChatEvent.class));
    }

    private static User user(Long id, String name, String email) {
        return User.builder().id(id).fullName(name).email(email).role(Role.CUSTOMER).active(true).build();
    }
}
