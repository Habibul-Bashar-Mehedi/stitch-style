package com.stitchstyle.app.scheduler;

import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.model.entity.ReservationStatus;
import com.stitchstyle.app.model.entity.StockReservation;
import com.stitchstyle.app.repository.ChatMessageRepository;
import com.stitchstyle.app.repository.IdempotencyKeyRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import com.stitchstyle.app.service.InventoryService;
import com.stitchstyle.app.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerJobsTest {

    @Test
    void outboxJobDelegatesToNotificationService() {
        NotificationService notificationService = mock(NotificationService.class);
        new OutboxDispatchJob(notificationService).dispatch();
        verify(notificationService).dispatchDue();
    }

    @Test
    void idempotencyCleanupDeletesExpiredKeys() {
        IdempotencyKeyRepository repository = mock(IdempotencyKeyRepository.class);
        when(repository.deleteByExpiresAtBefore(any())).thenReturn(3L);

        new IdempotencyCleanupJob(repository).purgeExpired();

        verify(repository).deleteByExpiresAtBefore(any());
    }

    @Test
    void reservationExpiryReleasesAndCancelsFullyExpiredPrepaidOrders() {
        StockReservationRepository reservationRepository = mock(StockReservationRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        Order order = Order.builder().id(1L).orderNumber("ORD-1").status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.INITIATED).build();
        StockReservation reservation = StockReservation.builder().id(1L).order(order)
                .status(ReservationStatus.ACTIVE).expiresAt(Instant.now().minusSeconds(60)).build();
        when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), any()))
                .thenReturn(List.of(reservation));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(reservationRepository.findByOrderIdAndStatus(1L, ReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));

        new ReservationExpiryJob(reservationRepository, orderRepository, inventoryService).releaseExpired();

        verify(inventoryService).releaseReservations(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getCancelReason()).isEqualTo("PAYMENT_TIMEOUT");
    }

    @Test
    void reservationExpiryLeavesOrdersWithSomeActiveReservationsAlone() {
        StockReservationRepository reservationRepository = mock(StockReservationRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).paymentStatus(PaymentStatus.INITIATED).build();
        StockReservation expired = StockReservation.builder().id(1L).order(order)
                .status(ReservationStatus.ACTIVE).expiresAt(Instant.now().minusSeconds(60)).build();
        StockReservation stillActive = StockReservation.builder().id(2L).order(order)
                .status(ReservationStatus.ACTIVE).expiresAt(Instant.now().plusSeconds(600)).build();
        when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), any()))
                .thenReturn(List.of(expired));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(reservationRepository.findByOrderIdAndStatus(1L, ReservationStatus.ACTIVE))
                .thenReturn(List.of(expired, stillActive));

        new ReservationExpiryJob(reservationRepository, orderRepository, inventoryService).releaseExpired();

        verify(inventoryService, never()).releaseReservations(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void reservationExpirySkipsDeletedOrders() {
        StockReservationRepository reservationRepository = mock(StockReservationRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = Order.builder().id(1L).build();
        StockReservation expired = StockReservation.builder().order(order)
                .status(ReservationStatus.ACTIVE).expiresAt(Instant.now()).build();
        when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), any()))
                .thenReturn(List.of(expired));
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        new ReservationExpiryJob(reservationRepository, orderRepository, mock(InventoryService.class)).releaseExpired();

        verify(reservationRepository, never()).findByOrderIdAndStatus(anyLong(), any());
    }

    @Test
    void chatRetentionPurgesOnlyWhenTheAdvisoryLockIsAcquired() {
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyLong())).thenReturn(true);
        when(messageRepository.deleteOlderThan(any())).thenReturn(7);

        new ChatRetentionJob(messageRepository, jdbc).purge();

        verify(messageRepository).deleteOlderThan(any());
        // unlock always runs
        verify(jdbc, org.mockito.Mockito.times(2)).queryForObject(anyString(), eq(Boolean.class), anyLong());
    }

    @Test
    void chatRetentionDoesNothingWithoutTheLock() {
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyLong())).thenReturn(false);

        new ChatRetentionJob(messageRepository, jdbc).purge();

        verify(messageRepository, never()).deleteOlderThan(any());
        verify(jdbc, org.mockito.Mockito.times(1)).queryForObject(anyString(), eq(Boolean.class), anyLong());
    }
}
