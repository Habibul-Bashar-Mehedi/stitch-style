package com.stitchstyle.app.integration.notification;

import java.util.Map;

public interface NotificationSender {
    void send(String recipient, String template, Map<String, Object> payload);
}
