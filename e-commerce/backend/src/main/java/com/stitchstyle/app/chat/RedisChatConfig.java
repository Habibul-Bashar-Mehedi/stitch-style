package com.stitchstyle.app.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "app.chat.fanout", havingValue = "redis")
public class RedisChatConfig {

    @Bean
    RedisMessageListenerContainer chatRedisListenerContainer(RedisConnectionFactory connectionFactory,
                                                            RedisChatSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisChatEventBus.CHANNEL));
        return container;
    }
}
