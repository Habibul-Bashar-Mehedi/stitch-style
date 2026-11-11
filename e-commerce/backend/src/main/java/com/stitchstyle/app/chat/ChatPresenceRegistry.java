package com.stitchstyle.app.chat;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChatPresenceRegistry {

    private final ConcurrentMap<Long, AtomicInteger> sessions = new ConcurrentHashMap<>();

    public void online(Long userId) {
        sessions.computeIfAbsent(userId, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public void offline(Long userId) {
        AtomicInteger count = sessions.get(userId);
        if (count != null) {
            if (count.decrementAndGet() <= 0) sessions.remove(userId);
        }
    }

    public boolean isOnline(Long userId) {
        return sessions.containsKey(userId);
    }

    public int onlineCount() {
        return sessions.size();
    }
}
