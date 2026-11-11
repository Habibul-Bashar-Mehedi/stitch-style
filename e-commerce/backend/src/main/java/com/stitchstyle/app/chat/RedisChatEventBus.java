package com.stitchstyle.app.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.chat.fanout", havingValue = "redis")
public class RedisChatEventBus implements ChatEventBus {

    public static final String CHANNEL = "stitchstyle:chat:events";
    private static final Logger log = LoggerFactory.getLogger(RedisChatEventBus.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisChatEventBus(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(List<Long> recipientUserIds, ChatEvent event) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(
                    new Envelope(recipientUserIds, event)));
        } catch (Exception ex) {
            log.warn("chat event fan-out failed type={} error={}", event.type(), ex.getMessage());
        }
    }

    public record Envelope(List<Long> recipientUserIds, ChatEvent event) {
    }
}
