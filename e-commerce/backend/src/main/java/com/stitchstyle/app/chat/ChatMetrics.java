package com.stitchstyle.app.chat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {

    public ChatMetrics(MeterRegistry registry, ChatPresenceRegistry presence) {
        Gauge.builder("chat.sessions.online", presence, ChatPresenceRegistry::onlineCount)
                .description("Currently connected chat sessions")
                .register(registry);
    }
}
