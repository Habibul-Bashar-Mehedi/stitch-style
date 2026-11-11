package com.stitchstyle.app.chat;

import com.stitchstyle.app.service.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatRealtimeListener {

    private final ChatEventBus eventBus;

    public ChatRealtimeListener(ChatEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessage(ChatService.ChatMessageCreatedEvent event) {
        eventBus.publish(event.recipientIds(), ChatEvent.message(event.conversationId(), event.message()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRead(ChatService.ChatReadEvent event) {
        eventBus.publish(event.recipientIds(),
                ChatEvent.read(event.conversationId(), event.readerId(), event.lastReadMessageId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversation(ChatService.ChatConversationChangedEvent event) {
        eventBus.publish(event.recipientIds(), ChatEvent.conversation(event.conversationId(), event.change()));
    }

    @EventListener
    public void onTyping(ChatService.ChatTypingEvent event) {
        eventBus.publish(event.recipientIds(), ChatEvent.typing(event.conversationId(), event.name()));
    }
}
