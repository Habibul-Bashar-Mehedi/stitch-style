package com.stitchstyle.app.service;

import com.stitchstyle.app.integration.notification.EmailSender;
import com.stitchstyle.app.integration.notification.SmsSender;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final NotificationOutboxRepository repository;
    private final SmsSender smsSender;
    private final EmailSender emailSender;

    public NotificationService(NotificationOutboxRepository repository, SmsSender smsSender, EmailSender emailSender) {
        this.repository = repository;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
    }

    @Transactional
    public void enqueueOrderConfirmation(Order order) {
        enqueue(order, NotificationChannel.SMS, order.getCustomerPhone(), "ORDER_CONFIRMATION",
                Map.of("orderNumber", order.getOrderNumber(), "grandTotal", order.getGrandTotal().toPlainString()));
    }

    @Transactional
    public void resend(Order order) {
        enqueue(order, NotificationChannel.SMS, order.getCustomerPhone(), "ORDER_CONFIRMATION",
                Map.of("orderNumber", order.getOrderNumber(), "grandTotal", order.getGrandTotal().toPlainString()));
    }

    @Transactional
    public void enqueueReturnStatus(ReturnRequest returnRequest, String template) {
        Order order = returnRequest.getOrder();
        if (order == null || order.getUser() == null || order.getUser().getEmail() == null) return;
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("returnId", returnRequest.getId());
        payload.put("status", returnRequest.getStatus().name());
        if (returnRequest.getRefundAmount() != null) {
            payload.put("refundAmount", returnRequest.getRefundAmount().toPlainString());
        }
        enqueue(order, NotificationChannel.EMAIL, order.getUser().getEmail(), template, payload);
    }

    @Transactional
    public void enqueue(Order order, NotificationChannel channel, String recipient, String template,
                        Map<String, Object> payload) {
        repository.save(NotificationOutbox.builder()
                .order(order)
                .channel(channel)
                .recipient(recipient)
                .template(template)
                .payload(payload)
                .status(NotificationStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(Instant.now())
                .build());
    }

    @Transactional
    public void dispatchDue() {
        List<NotificationOutbox> due = repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                NotificationStatus.PENDING, Instant.now());
        for (NotificationOutbox message : due) {
            try {
                if (message.getChannel() == NotificationChannel.SMS) {
                    smsSender.send(message.getRecipient(), message.getTemplate(), message.getPayload());
                } else {
                    emailSender.send(message.getRecipient(), message.getTemplate(), message.getPayload());
                }
                message.setStatus(NotificationStatus.SENT);
                message.setSentAt(Instant.now());
                message.setLastError(null);
            } catch (Exception e) {
                message.setAttempts(message.getAttempts() + 1);
                message.setLastError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                if (message.getAttempts() >= MAX_ATTEMPTS) {
                    message.setStatus(NotificationStatus.FAILED);
                } else {
                    long delaySeconds = (long) Math.pow(2, message.getAttempts()) * 60L;
                    message.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(delaySeconds)));
                }
                log.warn("notification delivery failed orderId={} attempt={} error={}",
                        message.getOrder() == null ? null : message.getOrder().getId(),
                        message.getAttempts(), message.getLastError());
            }
            repository.save(message);
        }
    }
}
