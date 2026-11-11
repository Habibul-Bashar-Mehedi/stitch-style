package com.stitchstyle.app.chat;

import java.util.List;

public interface ChatEventBus {
    void publish(List<Long> recipientUserIds, ChatEvent event);
}
