package com.stitchstyle.app.integration.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.notifications.email.from:no-reply@stitchandstyle.bd}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host;
        this.from = from;
    }

    @Override
    public void send(String recipient, String template, Map<String, Object> payload) {
        if (host == null || host.isBlank()) {
            log.info("email provider not configured; recipient={} template={}", mask(recipient), template);
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("email sender bean unavailable; recipient={} template={}", mask(recipient), template);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject(template));
        message.setText(body(template, payload));
        mailSender.send(message);
    }

    private String subject(String template) {
        return switch (template) {
            case "ORDER_CONFIRMATION" -> "Your Stitch & Style order";
            case "ORDER_SHIPPED" -> "Your order has shipped";
            case "ORDER_DELIVERED" -> "Your order was delivered";
            case "COMPLAINT_RESPONSE" -> "We have responded to your complaint";
            case "RETURN_APPROVED" -> "Your return was approved";
            case "RETURN_REJECTED" -> "Your return request was rejected";
            case "RETURN_RECEIVED" -> "We received your return";
            case "RETURN_REFUNDED" -> "Your refund has been recorded";
            case "CHAT_MESSAGE_UNREAD" -> "You have a new support message";
            default -> "Stitch & Style update";
        };
    }

    private String body(String template, Map<String, Object> payload) {
        return template + "\n\n" + payload.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private String mask(String email) {
        return email == null ? "***" : email.replaceAll("(.{2}).*(@.*)", "$1***$2");
    }
}
