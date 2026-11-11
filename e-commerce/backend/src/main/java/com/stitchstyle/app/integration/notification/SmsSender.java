package com.stitchstyle.app.integration.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SmsSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    private final RestClient client;
    private final String url;
    private final String apiKey;
    private final String sender;

    public SmsSender(RestClient.Builder builder,
                     @Value("${app.notifications.sms.url:}") String url,
                     @Value("${app.notifications.sms.api-key:}") String apiKey,
                     @Value("${app.notifications.sms.sender:StitchStyle}") String sender) {
        this.client = builder.build();
        this.url = url;
        this.apiKey = apiKey;
        this.sender = sender;
    }

    @Override
    public void send(String recipient, String template, Map<String, Object> payload) {
        if (url == null || url.isBlank()) {
            log.info("sms provider not configured; recipient={} template={} payload={}", mask(recipient), template, payload);
            return;
        }
        client.post().uri(url)
                .header("Authorization", apiKey == null ? "" : "Bearer " + apiKey)
                .body(Map.of("to", recipient, "sender", sender, "template", template, "variables", payload))
                .retrieve()
                .toBodilessEntity();
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 5 ? "***" : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
    }
}
