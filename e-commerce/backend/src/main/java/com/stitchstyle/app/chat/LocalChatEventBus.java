package com.stitchstyle.app.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.chat.fanout", havingValue = "local", matchIfMissing = true)
public class LocalChatEventBus implements ChatEventBus {

    private final ChatRealtimeDelivery delivery;

    public LocalChatEventBus(ChatRealtimeDelivery delivery) {
        this.delivery = delivery;
    }

    @Override
    public void publish(List<Long> recipientUserIds, ChatEvent event) {
        delivery.deliver(recipientUserIds, event);
    }
}
