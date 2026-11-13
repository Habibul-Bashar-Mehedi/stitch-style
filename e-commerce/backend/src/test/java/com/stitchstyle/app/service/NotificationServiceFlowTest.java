package com.stitchstyle.app.service;

import com.stitchstyle.app.integration.notification.EmailSender;
import com.stitchstyle.app.integration.notification.SmsSender;
import com.stitchstyle.app.model.entity.NotificationChannel;
import com.stitchstyle.app.model.entity.NotificationOutbox;
import com.stitchstyle.app.model.entity.NotificationStatus;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.ReturnRequest;
import com.stitchstyle.app.model.entity.ReturnStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceFlowTest {

    private NotificationOutboxRepository repository;
    private SmsSender smsSender;
    private EmailSender emailSender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationOutboxRepository.class);
        smsSender = mock(SmsSender.class);
        emailSender = mock(EmailSender.class);
        service = new NotificationService(repository, smsSender, emailSender);
    }

    @Test
    void orderConfirmationIsQueuedForSms() {
        Order order = Order.builder().id(1L).orderNumber("ORD-1").customerPhone("+8801712345678")
                .grandTotal(new BigDecimal("1060.00")).build();

        service.enqueueOrderConfirmation(order);

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(captor.getValue().getRecipient()).isEqualTo("+8801712345678");
        assertThat(captor.getValue().getTemplate()).isEqualTo("ORDER_CONFIRMATION");
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void resendQueuesAnotherConfirmation() {
        service.resend(Order.builder().id(1L).orderNumber("ORD-1").customerPhone("+8801712345678")
                .grandTotal(BigDecimal.TEN).build());

        verify(repository).save(any(NotificationOutbox.class));
    }

    @Test
    void returnStatusSkipsOrdersWithoutAUserEmail() {
        ReturnRequest anonymous = ReturnRequest.builder().id(1L).status(ReturnStatus.APPROVED)
                .order(Order.builder().id(1L).orderNumber("ORD-1").build()).build();

        service.enqueueReturnStatus(anonymous, "RETURN_APPROVED");

        verify(repository, never()).save(any());
    }

    @Test
    void returnStatusQueuesAnEmailWithTheRefundAmount() {
        User user = User.builder().id(1L).email("c@x.com").fullName("C").role(Role.CUSTOMER).active(true).build();
        ReturnRequest returnRequest = ReturnRequest.builder().id(4L).status(ReturnStatus.REFUNDED)
                .refundAmount(new BigDecimal("500.00"))
                .order(Order.builder().id(1L).orderNumber("ORD-1").user(user).build()).build();

        service.enqueueReturnStatus(returnRequest, "RETURN_REFUNDED");

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(captor.getValue().getRecipient()).isEqualTo("c@x.com");
        assertThat(captor.getValue().getPayload()).containsEntry("refundAmount", "500.00");
    }

    @Test
    void fiveFailuresMarkTheMessageFailed() {
        NotificationOutbox message = NotificationOutbox.builder()
                .channel(NotificationChannel.SMS).recipient("+8801712345678")
                .template("ORDER_CONFIRMATION").payload(Map.of()).status(NotificationStatus.PENDING)
                .attempts(4).nextAttemptAt(Instant.now().minusSeconds(5)).build();
        when(repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(message));
        doThrow(new RuntimeException("down")).when(smsSender).send(anyString(), anyString(), any());

        service.dispatchDue();

        assertThat(message.getAttempts()).isEqualTo(5);
        assertThat(message.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(message.getLastError()).isEqualTo("down");
    }

    @Test
    void emailChannelsUseTheEmailSender() {
        NotificationOutbox message = NotificationOutbox.builder()
                .channel(NotificationChannel.EMAIL).recipient("c@x.com")
                .template("RETURN_APPROVED").payload(Map.of()).status(NotificationStatus.PENDING)
                .attempts(0).nextAttemptAt(Instant.now().minusSeconds(1)).build();
        when(repository.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(message));

        service.dispatchDue();

        verify(emailSender).send("c@x.com", "RETURN_APPROVED", Map.of());
        assertThat(message.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void enqueueUsesTheProvidedChannelAndPayload() {
        service.enqueue(null, NotificationChannel.EMAIL, "x@y.z", "TEMPLATE", Map.of("k", "v"));

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).containsEntry("k", "v");
        assertThat(captor.getValue().getNextAttemptAt()).isNotNull();
    }
}
