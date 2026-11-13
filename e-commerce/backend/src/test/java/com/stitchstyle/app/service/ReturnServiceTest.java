package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.RefundRequest;
import com.stitchstyle.app.dto.request.ReturnQuoteRequest;
import com.stitchstyle.app.dto.request.ReturnRequestCreate;
import com.stitchstyle.app.dto.request.ReturnStatusRequest;
import com.stitchstyle.app.dto.response.ReturnQuoteResponse;
import com.stitchstyle.app.exception.InvalidReturnException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderItem;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.ReturnCondition;
import com.stitchstyle.app.model.entity.ReturnItem;
import com.stitchstyle.app.model.entity.ReturnRequest;
import com.stitchstyle.app.model.entity.ReturnStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.ReturnItemRepository;
import com.stitchstyle.app.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnServiceTest {

    private ReturnRequestRepository returnRepository;
    private ReturnItemRepository returnItemRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private InventoryService inventoryService;
    private PaymentService paymentService;
    private NotificationService notificationService;
    private ReturnService service;
    private User customer;
    private Order order;
    private OrderItem firstItem;
    private OrderItem secondItem;

    @BeforeEach
    void setUp() {
        returnRepository = mock(ReturnRequestRepository.class);
        returnItemRepository = mock(ReturnItemRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        inventoryService = mock(InventoryService.class);
        paymentService = mock(PaymentService.class);
        notificationService = mock(NotificationService.class);
        service = new ReturnService(returnRepository, returnItemRepository, orderRepository, orderItemRepository,
                inventoryService, paymentService, mock(AuditService.class), notificationService, 7);

        customer = User.builder().id(1L).email("c@x.com").fullName("Customer")
                .role(Role.CUSTOMER).active(true).build();
        order = deliveredOrder();
        firstItem = orderItem(10L, order, "Panjabi", 2, new BigDecimal("500.00"));
        secondItem = orderItem(11L, order, "Shirt", 1, new BigDecimal("400.00"));
    }

    @Test
    void requestRequiresSignedInCustomer() {
        assertThatThrownBy(() -> service.request(null, create(List.of(
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.UNOPENED)))))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Sign in");
    }

    @Test
    void requestRequiresDeliveredOrderOwnedByTheCustomer() {
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        order.setUser(User.builder().id(99L).email("other@x.com").fullName("Other").build());

        assertThatThrownBy(() -> service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.UNOPENED)))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requestRejectsUndeliveredOrdersAndClosedWindows() {
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        order.setStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.UNOPENED)))))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("delivered");

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now().minus(8, ChronoUnit.DAYS));
        assertThatThrownBy(() -> service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.UNOPENED)))))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("window");
    }

    @Test
    void requestRejectsDuplicateActiveReturn() {
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(returnRepository.findFirstByOrderIdAndStatusNot(7L, ReturnStatus.REJECTED))
                .thenReturn(Optional.of(ReturnRequest.builder().id(1L).build()));

        assertThatThrownBy(() -> service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.UNOPENED)))))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void requestCreatesLinesAndRejectsTooManyItems() {
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(returnRepository.findFirstByOrderIdAndStatusNot(7L, ReturnStatus.REJECTED)).thenReturn(Optional.empty());
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of(firstItem, secondItem));
        when(returnRepository.save(any(ReturnRequest.class))).thenAnswer(invocation -> {
            ReturnRequest request = invocation.getArgument(0);
            request.setId(50L);
            request.setRequestedAt(Instant.now());
            return request;
        });
        when(returnItemRepository.findByReturnRequestId(50L)).thenReturn(List.of());

        var response = service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 2, ReturnCondition.UNOPENED))));

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.status()).isEqualTo(ReturnStatus.REQUESTED);
        verify(returnItemRepository).save(any(ReturnItem.class));

        assertThatThrownBy(() -> service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 3, ReturnCondition.UNOPENED)))))
                .isInstanceOfSatisfying(InvalidReturnException.class,
                        ex -> assertThat(((InvalidReturnException) ex).getCode()).isEqualTo("RETURN_QUANTITY_EXCEEDED"));
    }

    @Test
    void duplicateLinesMustShareACondition() {
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        when(returnRepository.findFirstByOrderIdAndStatusNot(7L, ReturnStatus.REJECTED)).thenReturn(Optional.empty());
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of(firstItem));

        assertThatThrownBy(() -> service.request(customer, create(List.of(
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.UNOPENED),
                new ReturnRequestCreate.Item(10L, 1, ReturnCondition.USED)))))
                .isInstanceOfSatisfying(InvalidReturnException.class,
                        ex -> assertThat(((InvalidReturnException) ex).getCode()).isEqualTo("RETURN_DUPLICATE_ITEM"));
    }

    @Test
    void quoteSharesCouponAndIncludesDeliveryOnlyForAFullReturn() {
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));
        order.setSubtotal(new BigDecimal("1400.00"));
        order.setCouponDiscount(new BigDecimal("140.00"));
        order.setDeliveryCharge(new BigDecimal("60.00"));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of(firstItem, secondItem));

        ReturnQuoteResponse partial = service.quote(customer, "ORD-1",
                new ReturnQuoteRequest(List.of(new ReturnQuoteRequest.Item(10L, 1))));
        assertThat(partial.itemsAmount()).isEqualByComparingTo("500.00");
        assertThat(partial.couponDiscountShare()).isEqualByComparingTo("50.00");
        assertThat(partial.deliveryAmount()).isEqualByComparingTo("0.00");
        assertThat(partial.total()).isEqualByComparingTo("450.00");
        assertThat(partial.fullOrder()).isFalse();

        ReturnQuoteResponse full = service.quote(customer, "ORD-1",
                new ReturnQuoteRequest(List.of(new ReturnQuoteRequest.Item(10L, 2),
                        new ReturnQuoteRequest.Item(11L, 1))));
        assertThat(full.itemsAmount()).isEqualByComparingTo("1400.00");
        assertThat(full.couponDiscountShare()).isEqualByComparingTo("140.00");
        assertThat(full.deliveryAmount()).isEqualByComparingTo("60.00");
        assertThat(full.total()).isEqualByComparingTo("1320.00");
        assertThat(full.fullOrder()).isTrue();
        assertThat(full.windowEndsAt()).isEqualTo(order.getDeliveredAt().plus(7, ChronoUnit.DAYS));
    }

    @Test
    void advanceRestocksWhenReceivedAndNotifiesStatusChanges() {
        ReturnRequest request = ReturnRequest.builder().id(30L).order(order).status(ReturnStatus.APPROVED)
                .requestedAt(Instant.now()).build();
        OrderItem item = firstItem;
        ReturnItem returnItem = ReturnItem.builder().returnRequest(request).orderItem(item).quantity(1)
                .condition(ReturnCondition.UNOPENED).build();
        when(returnRepository.findById(30L)).thenReturn(Optional.of(request));
        when(returnItemRepository.findByReturnRequestId(30L)).thenReturn(List.of(returnItem));

        var response = service.advance(30L, new ReturnStatusRequest(ReturnStatus.RECEIVED, "  arrived "));

        assertThat(response.status()).isEqualTo(ReturnStatus.RECEIVED);
        assertThat(request.getResolutionNote()).isEqualTo("arrived");
        verify(inventoryService).restockReturn(order, item.getVariant().getId(), 1);
        verify(notificationService).enqueueReturnStatus(request, "RETURN_RECEIVED");
    }

    @Test
    void advanceRejectsIllegalTransitions() {
        ReturnRequest request = ReturnRequest.builder().id(31L).order(order).status(ReturnStatus.REQUESTED)
                .requestedAt(Instant.now()).build();
        when(returnRepository.findById(31L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.advance(31L, new ReturnStatusRequest(ReturnStatus.REFUNDED, null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void refundRequiresReceivedStatusAndValidAmount() {
        ReturnRequest request = ReturnRequest.builder().id(32L).order(order).status(ReturnStatus.APPROVED)
                .requestedAt(Instant.now()).build();
        when(returnRepository.findById(32L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.refund(32L, new RefundRequest("MANUAL", null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void refundRejectsAmountsOutsideTheQuotedRange() {
        ReturnRequest request = ReturnRequest.builder().id(33L).order(order).status(ReturnStatus.RECEIVED)
                .requestedAt(Instant.now()).build();
        when(returnRepository.findById(33L)).thenReturn(Optional.of(request));
        when(returnItemRepository.findByReturnRequestId(33L)).thenReturn(List.of(ReturnItem.builder()
                .returnRequest(request).orderItem(firstItem).quantity(1).condition(ReturnCondition.UNOPENED).build()));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of(firstItem, secondItem));

        assertThatThrownBy(() -> service.refund(33L, new RefundRequest("MANUAL", new BigDecimal("99999.00"))))
                .isInstanceOf(InvalidReturnException.class);
        assertThatThrownBy(() -> service.refund(33L, new RefundRequest("MANUAL", BigDecimal.ZERO)))
                .isInstanceOf(InvalidReturnException.class);
    }

    @Test
    void fullRefundMarksOrderRefundedAndRecordsMethod() {
        ReturnRequest request = ReturnRequest.builder().id(34L).order(order).status(ReturnStatus.RECEIVED)
                .requestedAt(Instant.now()).build();
        ReturnItem item = ReturnItem.builder().returnRequest(request).orderItem(firstItem).quantity(2)
                .condition(ReturnCondition.UNOPENED).build();
        when(returnRepository.findById(34L)).thenReturn(Optional.of(request));
        when(returnItemRepository.findByReturnRequestId(34L)).thenReturn(List.of(item));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of(firstItem));
        when(paymentService.refund(order, new BigDecimal("1060.00"), "RETURN_34")).thenReturn("MANUAL_COD");

        var response = service.refund(34L, new RefundRequest("MANUAL", new BigDecimal("1060.00")));

        assertThat(response.status()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(request.getRefundMethod()).isEqualTo("MANUAL_COD");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(notificationService).enqueueReturnStatus(request, "RETURN_REFUNDED");
    }

    @Test
    void listAndGetFailForUnknownReturn() {
        when(returnRepository.findAllByOrderByRequestedAtDesc()).thenReturn(List.of());
        assertThat(service.list()).isEmpty();

        when(returnRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void enrichOrdersSummarizesEligibility() {
        when(returnRepository.findByOrderUserIdOrderByRequestedAtDesc(1L)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of(firstItem, secondItem));

        var response = service.enrichOrders(customer, List.of(new com.stitchstyle.app.dto.response.TrackingResponse(
                "ORD-1", OrderStatus.DELIVERED, PaymentStatus.UNPAID, new BigDecimal("1400.00"),
                order.getPlacedAt(), null, order.getDeliveredAt(), List.of())));

        assertThat(response).singleElement().satisfies(myOrder -> {
            assertThat(myOrder.returnInfo().eligibility())
                    .isEqualTo(com.stitchstyle.app.dto.response.MyOrderResponse.Eligibility.ELIGIBLE);
            assertThat(myOrder.returnInfo().windowEndsAt()).isNotNull();
        });
    }

    private ReturnRequestCreate create(List<ReturnRequestCreate.Item> items) {
        return new ReturnRequestCreate("ORD-1", "reason", items);
    }

    private Order deliveredOrder() {
        return Order.builder().id(7L).orderNumber("ORD-1").user(customer)
                .customerName("Customer").customerPhone("+8801712345678").shippingAddress("Dhaka")
                .subtotal(new BigDecimal("1400.00")).deliveryCharge(new BigDecimal("60.00"))
                .discountTotal(BigDecimal.ZERO).couponDiscount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("1060.00")).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID).status(OrderStatus.DELIVERED)
                .placedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .deliveredAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
    }

    private OrderItem orderItem(Long id, Order order, String name, int quantity, BigDecimal unit) {
        Product product = Product.builder().id(id + 100).slug("p-" + id).nameI18n(java.util.Map.of("en", name))
                .basePrice(unit).active(true).build();
        ProductVariant variant = ProductVariant.builder().id(id + 200).product(product).sizeLabel("M")
                .sku("SKU-" + id).stockQuantity(10).active(true).build();
        return OrderItem.builder().id(id).order(order).product(product).variant(variant)
                .productNameSnapshot(name).sizeLabelSnapshot("M").skuSnapshot("SKU-" + id)
                .unitPriceSnapshot(unit).quantity(quantity)
                .lineTotal(unit.multiply(BigDecimal.valueOf(quantity))).build();
    }
}
