package com.stitchstyle.app;

import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CheckoutCustomer;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.response.OrderResponse;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.ChatConversation;
import com.stitchstyle.app.model.entity.ChatConversationStatus;
import com.stitchstyle.app.model.entity.ChatConversationType;
import com.stitchstyle.app.model.entity.ChatMessage;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.model.entity.IdempotencyKey;
import com.stitchstyle.app.model.entity.NotificationChannel;
import com.stitchstyle.app.model.entity.NotificationOutbox;
import com.stitchstyle.app.model.entity.NotificationStatus;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.ReservationStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.StockReservation;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.ChatConversationRepository;
import com.stitchstyle.app.repository.ChatMessageRepository;
import com.stitchstyle.app.repository.IdempotencyKeyRepository;
import com.stitchstyle.app.repository.NotificationOutboxRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import com.stitchstyle.app.scheduler.ChatRetentionJob;
import com.stitchstyle.app.scheduler.IdempotencyCleanupJob;
import com.stitchstyle.app.scheduler.OutboxDispatchJob;
import com.stitchstyle.app.scheduler.ReservationExpiryJob;
import com.stitchstyle.app.service.OrderService;
import com.stitchstyle.app.service.NotificationService;
import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired NotificationService notificationService;
    @Autowired ReservationExpiryJob reservationExpiryJob;
    @Autowired IdempotencyCleanupJob idempotencyCleanupJob;
    @Autowired OutboxDispatchJob outboxDispatchJob;
    @Autowired ChatRetentionJob chatRetentionJob;
    @Autowired OrderRepository orderRepository;
    @Autowired StockReservationRepository reservationRepository;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired NotificationOutboxRepository notificationOutboxRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired ChatConversationRepository chatConversationRepository;

    @Test
    void expiredReservationsReleaseStockAndCancelUnpaidOrders() {
        String unique = suffix();
        Fixture fixture = fixture(unique);
        OrderResponse placed = place(fixture, "it-sched-" + unique + "-0001", PaymentMethod.BKASH);
        Order order = orderRepository.findByOrderNumber(placed.orderNumber()).orElseThrow();
        StockReservation reservation = reservationRepository
                .findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE).get(0);
        reservation.setExpiresAt(Instant.now().minusSeconds(60));
        reservationRepository.saveAndFlush(reservation);

        reservationExpiryJob.releaseExpired();

        assertThat(reservationRepository.findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE))
                .isEmpty();
        Order expired = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(expired.getCancelReason()).isEqualTo("PAYMENT_TIMEOUT");
        ProductVariant variant = variantRepository.findById(fixture.variant.getId()).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(3);
        assertThat(variant.getReservedQuantity()).isZero();
    }

    @Test
    void reservationExpiryLeavesFreshReservationsAlone() {
        String unique = suffix();
        Fixture fixture = fixture(unique);
        OrderResponse placed = place(fixture, "it-sched-" + unique + "-0002", PaymentMethod.BKASH);
        Order order = orderRepository.findByOrderNumber(placed.orderNumber()).orElseThrow();

        reservationExpiryJob.releaseExpired();

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(reservationRepository.findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    void idempotencyCleanupDeletesOnlyExpiredKeys() {
        String unique = suffix();
        IdempotencyKey expired = idempotencyKeyRepository.saveAndFlush(IdempotencyKey.builder()
                .scopeKey("guest:it-" + unique).idempotencyKey("expired-" + unique)
                .requestHash("hash").expiresAt(Instant.now().minusSeconds(3600)).build());
        IdempotencyKey live = idempotencyKeyRepository.saveAndFlush(IdempotencyKey.builder()
                .scopeKey("guest:it-" + unique).idempotencyKey("live-" + unique)
                .requestHash("hash").expiresAt(Instant.now().plusSeconds(3600)).build());

        idempotencyCleanupJob.purgeExpired();

        assertThat(idempotencyKeyRepository.findById(expired.getId())).isEmpty();
        assertThat(idempotencyKeyRepository.findById(live.getId())).isPresent();
    }

    @Test
    void outboxDispatchMarksOrderConfirmationSent() {
        String unique = suffix();
        Fixture fixture = fixture(unique);
        OrderResponse placed = place(fixture, "it-sched-" + unique + "-0003", PaymentMethod.COD);
        Order order = orderRepository.findByOrderNumber(placed.orderNumber()).orElseThrow();

        notificationService.dispatchDue();

        List<NotificationOutbox> messages = notificationOutboxRepository.findAll().stream()
                .filter(message -> message.getOrder() != null && message.getOrder().getId().equals(order.getId()))
                .toList();
        assertThat(messages).isNotEmpty();
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getChannel()).isEqualTo(NotificationChannel.SMS);
            assertThat(message.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(message.getSentAt()).isNotNull();
        });

        // Running the scheduler job again is a harmless no-op.
        outboxDispatchJob.dispatch();
        assertThat(notificationOutboxRepository.findAll().stream()
                .filter(message -> message.getOrder() != null && message.getOrder().getId().equals(order.getId()))
                .allMatch(message -> message.getStatus() == NotificationStatus.SENT)).isTrue();
    }

    @Test
    void chatRetentionPurgesMessagesOlderThanThirtyDays() {
        String unique = suffix();
        User sender = ensureUser("it-retention-" + unique + "@example.com", Role.CUSTOMER);
        ChatConversation conversation = chatConversationRepository.save(ChatConversation.builder()
                .type(ChatConversationType.SUPPORT).status(ChatConversationStatus.OPEN)
                .customer(sender).build());
        ChatMessage old = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .conversation(conversation).sender(sender).clientMessageId("old-" + unique)
                .body("old").createdAt(Instant.now().minus(31, ChronoUnit.DAYS)).build());
        ChatMessage recent = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .conversation(conversation).sender(sender).clientMessageId("new-" + unique)
                .body("new").createdAt(Instant.now()).build());

        chatRetentionJob.purge();

        assertThat(chatMessageRepository.findById(old.getId())).isEmpty();
        assertThat(chatMessageRepository.findById(recent.getId())).isPresent();
    }

    private OrderResponse place(Fixture fixture, String key, PaymentMethod method) {
        return orderService.place(new OrderRequest(new CheckoutCustomer("Scheduler Customer", "01712345678"),
                        "House 1, Dhaka", fixture.area.getId(), method, null, null,
                        List.of(new CartLineRequest(fixture.variant.getId(), 1, true))),
                null, "it-sched-guest-" + fixture.tag, key, "en");
    }

    private Fixture fixture(String unique) {
        Category category = ensureCategory("it-sched-cat-" + unique, "Scheduler Category " + unique, null, false);
        Product product = ensureProduct(category, "it-sched-product-" + unique, "Scheduler Product " + unique,
                "শিডিউলার", new BigDecimal("1000.00"), null);
        ProductVariant variant = ensureVariant(product, "M", "IT-SCHED-" + unique.toUpperCase(), 3, 0);
        DeliveryArea area = ensureDeliveryArea("IT Scheduler Area " + unique, new BigDecimal("60.00"), null);
        return new Fixture(unique, variant, area);
    }

    private record Fixture(String tag, ProductVariant variant, DeliveryArea area) {
    }
}
