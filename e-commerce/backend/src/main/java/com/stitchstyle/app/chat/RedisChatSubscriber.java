package com.stitchstyle.app.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.chat.fanout", havingValue = "redis")
public class RedisChatSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisChatSubscriber.class);

    private final ObjectMapper objectMapper;
    private final ChatRealtimeDelivery delivery;

    public RedisChatSubscriber(ObjectMapper objectMapper, ChatRealtimeDelivery delivery) {
        this.objectMapper = objectMapper;
        this.delivery = delivery;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RedisChatEventBus.Envelope envelope = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), RedisChatEventBus.Envelope.class);
            delivery.deliver(envelope.recipientUserIds(), envelope.event());
        } catch (Exception ex) {
            log.warn("chat event fan-out decode failed: {}", ex.getMessage());
        }
    }
}
