package com.stitchstyle.app;

import com.stitchstyle.app.integration.notification.EmailSender;
import com.stitchstyle.app.integration.notification.SmsSender;
import com.stitchstyle.app.model.entity.NotificationChannel;
import com.stitchstyle.app.model.entity.NotificationOutbox;
import com.stitchstyle.app.model.entity.NotificationStatus;
import com.stitchstyle.app.repository.NotificationOutboxRepository;
import com.stitchstyle.app.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationOutboxRepository repository;
    private SmsSender smsSender;
    private EmailSender emailSender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(NotificationOutboxRepository.class);
        smsSender = Mockito.mock(SmsSender.class);
        emailSender = Mockito.mock(EmailSender.class);
        service = new NotificationService(repository, smsSender, emailSender);
    }

    @Test
    void dispatchMarksMessageSent() {
        NotificationOutbox message = message();
        when(repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(message));
        service.dispatchDue();
        verify(smsSender).send(message.getRecipient(), message.getTemplate(), message.getPayload());
        assertThat(message.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(message.getSentAt()).isNotNull();
    }

    @Test
    void failureSchedulesRetryWithBackoff() {
        NotificationOutbox message = message();
        when(repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(message));
        doThrow(new RuntimeException("provider down")).when(smsSender).send(anyString(), anyString(), any());
        service.dispatchDue();
        assertThat(message.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getLastError()).contains("provider down");
    }

    private NotificationOutbox message() {
        return NotificationOutbox.builder()
                .channel(NotificationChannel.SMS)
                .recipient("+8801712345678")
                .template("ORDER_CONFIRMATION")
                .payload(Map.of("orderNumber", "ORD-1"))
                .status(NotificationStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(Instant.now().minusSeconds(5))
                .build();
    }
}
