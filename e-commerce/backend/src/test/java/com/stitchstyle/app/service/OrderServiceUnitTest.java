package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CheckoutCustomer;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.exception.IdempotencyInProgressException;
import com.stitchstyle.app.exception.IdempotencyKeyReusedException;
import com.stitchstyle.app.exception.InvalidPhoneException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.MissingIdempotencyKeyException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.IdempotencyKey;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.IdempotencyKeyRepository;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceUnitTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private IdempotencyKeyRepository idempotencyRepository;
    private InventoryService inventoryService;
    private AuditService auditService;
    private OrderService service;
    private tools.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        idempotencyRepository = mock(IdempotencyKeyRepository.class);
        inventoryService = mock(InventoryService.class);
        auditService = mock(AuditService.class);
        objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();
        service = new OrderService(orderRepository, orderItemRepository, mock(ProductVariantRepository.class),
                mock(UserRepository.class), idempotencyRepository, mock(DeliveryAreaService.class),
                inventoryService, mock(PaymentService.class), mock(CartService.class),
                mock(NotificationService.class), mock(CouponService.class), auditService, objectMapper,
                Duration.ofMinutes(15), Duration.ofHours(24));
    }

    @Test
    void normalizePhoneAcceptsTheThreeSupportedShapes() {
        assertThat(OrderService.normalizePhone("01712345678")).isEqualTo("+8801712345678");
        assertThat(OrderService.normalizePhone("+8801712345678")).isEqualTo("+8801712345678");
        assertThat(OrderService.normalizePhone("8801712345678")).isEqualTo("+8801712345678");
        assertThat(OrderService.normalizePhone(" 017-1234 5678 ")).isEqualTo("+8801712345678");
    }

    @Test
    void normalizePhoneRejectsInvalidNumbers() {
        assertThatThrownBy(() -> OrderService.normalizePhone(null)).isInstanceOf(InvalidPhoneException.class);
        assertThatThrownBy(() -> OrderService.normalizePhone("0123456789")).isInstanceOf(InvalidPhoneException.class);
        assertThatThrownBy(() -> OrderService.normalizePhone("not-a-phone")).isInstanceOf(InvalidPhoneException.class);
        assertThatThrownBy(() -> OrderService.normalizePhone("0171234567")).isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void missingOrMalformedIdempotencyKeyIsRejectedBeforeAnyWork() {
        OrderRequest request = request();

        assertThatThrownBy(() -> service.place(request, null, "guest-1", null, "en"))
                .isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> service.place(request, null, "guest-1", "short", "en"))
                .isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessageContaining("16");
        assertThatThrownBy(() -> service.place(request, null, "guest-1", "bad key! with spaces", "en"))
                .isInstanceOf(MissingIdempotencyKeyException.class);

        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void guestCheckoutRequiresAGuestId() {
        assertThatThrownBy(() -> service.place(request(), null, " ", "8e03978e-40d5-43e8", "en"))
                .isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessageContaining("guest_id");
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() {
        OrderRequest request = request();
        IdempotencyKey existing = IdempotencyKey.builder()
                .scopeKey("guest:guest-1").idempotencyKey("8e03978e-40d5-43e8")
                .requestHash("a-different-hash").build();
        when(idempotencyRepository.findByScopeKeyAndIdempotencyKey("guest:guest-1", "8e03978e-40d5-43e8"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.place(request, null, "guest-1", "8e03978e-40d5-43e8", "en"))
                .isInstanceOf(IdempotencyKeyReusedException.class)
                .hasMessageContaining("different request body");
    }

    @Test
    void aKeyStillBeingProcessedIsRejected() throws Exception {
        OrderRequest request = request();
        IdempotencyKey existing = IdempotencyKey.builder()
                .scopeKey("guest:guest-1").idempotencyKey("8e03978e-40d5-43e8")
                .requestHash(hash(request)).responseStatus(null).build();
        when(idempotencyRepository.findByScopeKeyAndIdempotencyKey("guest:guest-1", "8e03978e-40d5-43e8"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.place(request, null, "guest-1", "8e03978e-40d5-43e8", "en"))
                .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void completedKeyReplaysTheStoredResponse() throws Exception {
        OrderRequest request = request();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("orderNumber", "ORD-1");
        body.put("publicToken", "tok");
        body.put("status", "PENDING");
        body.put("paymentStatus", "UNPAID");
        body.put("currency", "BDT");
        body.put("subtotal", new BigDecimal("100.00"));
        body.put("deliveryCharge", new BigDecimal("60.00"));
        body.put("discountTotal", BigDecimal.ZERO);
        body.put("grandTotal", new BigDecimal("160.00"));
        body.put("trackingUrl", "/track?token=tok");
        body.put("replayed", false);
        IdempotencyKey existing = IdempotencyKey.builder()
                .scopeKey("guest:guest-1").idempotencyKey("8e03978e-40d5-43e8")
                .requestHash(hash(request)).responseStatus(201).responseBody(body).build();
        when(idempotencyRepository.findByScopeKeyAndIdempotencyKey("guest:guest-1", "8e03978e-40d5-43e8"))
                .thenReturn(Optional.of(existing));

        var response = service.place(request, null, "guest-1", "8e03978e-40d5-43e8", "en");

        assertThat(response.replayed()).isTrue();
        assertThat(response.orderNumber()).isEqualTo("ORD-1");
        assertThat(response.grandTotal()).isEqualByComparingTo("160.00");
        verify(inventoryService, never()).lockAndValidate(any());
    }

    @Test
    void cancelRestoresInventoryAndStampsCancellation() {
        Order order = order(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        var response = service.updateStatus(1L, OrderStatus.CANCELLED, "Customer asked");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getCancelReason()).isEqualTo("Customer asked");
        verify(inventoryService).restoreOnCancel(order);
        verify(auditService).record(any(), any(), anyLong(), any(), any());
    }

    @Test
    void shippedThenDeliveredStampsTimestamps() {
        Order order = order(2L, OrderStatus.PENDING);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of());

        service.updateStatus(2L, OrderStatus.SHIPPED, null);
        assertThat(order.getShippedAt()).isNotNull();

        service.updateStatus(2L, OrderStatus.DELIVERED, null);
        assertThat(order.getDeliveredAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void illegalTransitionsAreRejected() {
        Order order = order(3L, OrderStatus.PENDING);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(3L, OrderStatus.DELIVERED, null))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot move order");

        Order cancelled = order(4L, OrderStatus.CANCELLED);
        when(orderRepository.findById(4L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.updateStatus(4L, OrderStatus.SHIPPED, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void trackingAndLookupsHandleMissingRecords() {
        when(orderRepository.findByPublicToken("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.trackByToken("missing")).isInstanceOf(ResourceNotFoundException.class);

        when(orderRepository.findByOrderNumberAndCustomerPhone("ORD-X", "+8801712345678"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.trackByNumberAndPhone("ORD-X", "01712345678"))
                .isInstanceOf(ResourceNotFoundException.class);

        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRequired(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void myOrderEnforcesOwnership() {
        User other = User.builder().id(2L).email("o@x.com").fullName("Other").role(Role.CUSTOMER).active(true).build();
        when(orderRepository.findByOrderNumber("ORD-1"))
                .thenReturn(Optional.of(order(5L, OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.myOrder(1L, "ORD-1")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(other.getId()).isEqualTo(2L);
    }

    @Test
    void myOrdersMapsEachOrderToTracking() {
        Order order = order(6L, OrderStatus.PENDING);
        when(orderRepository.findByUserIdOrderByPlacedAtDesc(1L)).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(6L)).thenReturn(List.of());

        assertThat(service.myOrders(1L)).singleElement()
                .extracting(response -> response.orderNumber()).isEqualTo("ORD-6");
    }

    @Test
    void adminListClampsPagingAndMapsRows() {
        Order order = order(7L, OrderStatus.PENDING);
        when(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        var response = service.adminList(OrderStatus.PENDING, LocalDate.now(), LocalDate.now(),
                "01712345678", "ORD-7", -5, 500);

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isZero();
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(orderRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void recordCourierUpdatesAndAudits() {
        Order order = order(8L, OrderStatus.SHIPPED);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        service.recordCourier(8L, "Pathao", "TRACK-1");

        assertThat(order.getCourierName()).isEqualTo("Pathao");
        assertThat(order.getTrackingNumber()).isEqualTo("TRACK-1");
        verify(auditService).record(any(), any(), anyLong(), any(), any());
    }

    private String hash(OrderRequest request) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(request);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    }

    private static OrderRequest request() {
        return new OrderRequest(new CheckoutCustomer("Nusrat", "01712345678"),
                "House 1, Dhaka", 1L, PaymentMethod.COD, null, null,
                List.of(new CartLineRequest(1L, 1, true)));
    }

    private static Order order(Long id, OrderStatus status) {
        return Order.builder().id(id).orderNumber("ORD-" + id).publicToken("tok-" + id)
                .customerName("Nusrat").customerPhone("+8801712345678").shippingAddress("Dhaka")
                .subtotal(new BigDecimal("100.00")).deliveryCharge(new BigDecimal("60.00"))
                .discountTotal(BigDecimal.ZERO).couponDiscount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("160.00")).paymentMethod(PaymentMethod.COD)
                .paymentStatus(com.stitchstyle.app.model.entity.PaymentStatus.UNPAID)
                .status(status).placedAt(Instant.now()).build();
    }
}
