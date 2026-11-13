package com.stitchstyle.app.service;

import com.stitchstyle.app.exception.PaymentVerificationException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.integration.payment.PaymentGateway;
import com.stitchstyle.app.integration.payment.PaymentGatewayRegistry;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.Payment;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentGatewayRegistry registry;
    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private InventoryService inventoryService;
    private PaymentService service;
    private PaymentGateway gateway;

    @BeforeEach
    void setUp() {
        registry = mock(PaymentGatewayRegistry.class);
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        inventoryService = mock(InventoryService.class);
        gateway = mock(PaymentGateway.class);
        service = new PaymentService(registry, paymentRepository, orderRepository, inventoryService);
    }

    @Test
    void codNeverCallsAGateway() {
        Order order = order(PaymentMethod.COD);

        assertThat(service.initiate(order)).isNull();
        assertThat(service.refund(order, BigDecimal.TEN, "x")).isEqualTo("MANUAL_COD");
        verify(registry, never()).get(any());
    }

    @Test
    void initiateStoresAProviderTransaction() {
        Order order = order(PaymentMethod.BKASH);
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.providerCode()).thenReturn("BKASH");
        when(gateway.initiate(order)).thenReturn(new PaymentGateway.PaymentInitResult("https://pay", "TXN-1"));

        assertThat(service.initiate(order)).isEqualTo("https://pay");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void invalidCallbackSignatureIsRejected() {
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                false, false, "TXN", BigDecimal.TEN, "BDT", "bad signature"));

        assertThatThrownBy(() -> service.handleCallback("BKASH", Map.of()))
                .isInstanceOf(PaymentVerificationException.class);
    }

    @Test
    void unknownPaymentIsRejectedAfterVerification() {
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                true, true, "TXN", BigDecimal.TEN, "BDT", "ok"));
        when(paymentRepository.findByProviderAndProviderTxnId("BKASH", "TXN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleCallback("BKASH", Map.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void paidCallbackCommitsReservationsAndMarksOrderPaid() {
        Order order = order(PaymentMethod.BKASH);
        Payment payment = Payment.builder().id(1L).order(order).provider("BKASH")
                .providerTxnId("TXN").amount(new BigDecimal("100.00")).currency("BDT")
                .status("INITIATED").build();
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                true, true, "TXN", new BigDecimal("100.00"), "BDT", "ok"));
        when(paymentRepository.findByProviderAndProviderTxnId("BKASH", "TXN")).thenReturn(Optional.of(payment));

        service.handleCallback("BKASH", Map.of());

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(inventoryService).commitReservations(order);
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }

    @Test
    void failedCallbackReleasesReservations() {
        Order order = order(PaymentMethod.BKASH);
        Payment payment = Payment.builder().order(order).provider("BKASH").providerTxnId("TXN")
                .amount(new BigDecimal("100.00")).currency("BDT").status("INITIATED").build();
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                true, false, "TXN", new BigDecimal("100.00"), "BDT", "failed"));
        when(paymentRepository.findByProviderAndProviderTxnId("BKASH", "TXN")).thenReturn(Optional.of(payment));

        service.handleCallback("BKASH", Map.of());

        assertThat(payment.getStatus()).isEqualTo("FAILED");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(inventoryService).releaseReservations(order);
    }

    @Test
    void alreadyPaidCallbacksAreIdempotent() {
        Payment payment = Payment.builder().order(order(PaymentMethod.BKASH)).provider("BKASH")
                .providerTxnId("TXN").amount(new BigDecimal("100.00")).currency("BDT").status("PAID").build();
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                true, true, "TXN", new BigDecimal("100.00"), "BDT", "ok"));
        when(paymentRepository.findByProviderAndProviderTxnId("BKASH", "TXN")).thenReturn(Optional.of(payment));

        service.handleCallback("BKASH", Map.of());

        verify(inventoryService, never()).commitReservations(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void amountOrCurrencyMismatchIsRejected() {
        Payment payment = Payment.builder().order(order(PaymentMethod.BKASH)).provider("BKASH")
                .providerTxnId("TXN").amount(new BigDecimal("100.00")).currency("BDT").status("INITIATED").build();
        when(registry.get("BKASH")).thenReturn(gateway);
        when(paymentRepository.findByProviderAndProviderTxnId("BKASH", "TXN")).thenReturn(Optional.of(payment));

        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                true, true, "TXN", new BigDecimal("99.00"), "BDT", "ok"));
        assertThatThrownBy(() -> service.handleCallback("BKASH", Map.of()))
                .isInstanceOf(PaymentVerificationException.class);

        when(gateway.verifyCallback(any())).thenReturn(new PaymentGateway.PaymentVerification(
                true, true, "TXN", new BigDecimal("100.00"), "USD", "ok"));
        assertThatThrownBy(() -> service.handleCallback("BKASH", Map.of()))
                .isInstanceOf(PaymentVerificationException.class);
    }

    @Test
    void refundWithoutAPaymentFallsBackToManual() {
        Order order = order(PaymentMethod.BKASH);
        order.setId(5L);
        when(paymentRepository.findByOrderId(5L)).thenReturn(Optional.empty());

        assertThat(service.refund(order, BigDecimal.TEN, "x")).isEqualTo("MANUAL");
    }

    @Test
    void refundUsesTheProviderAndRejectsProviderFailures() {
        Order order = order(PaymentMethod.BKASH);
        order.setId(5L);
        Payment payment = Payment.builder().order(order).provider("BKASH").providerTxnId("TXN")
                .amount(new BigDecimal("100.00")).currency("BDT").status("PAID").build();
        when(paymentRepository.findByOrderId(5L)).thenReturn(Optional.of(payment));
        when(registry.get("BKASH")).thenReturn(gateway);
        when(gateway.refund(eq("TXN"), any(), any()))
                .thenReturn(new PaymentGateway.RefundResult(true, "REF", "ok"));

        assertThat(service.refund(order, BigDecimal.TEN, "return")).isEqualTo("BKASH");
        assertThat(payment.getStatus()).isEqualTo("REFUNDED");

        when(gateway.refund(eq("TXN"), any(), any()))
                .thenReturn(new PaymentGateway.RefundResult(false, null, "declined"));
        assertThatThrownBy(() -> service.refund(order, BigDecimal.TEN, "return"))
                .isInstanceOf(PaymentVerificationException.class)
                .hasMessageContaining("declined");
    }

    private static Order order(PaymentMethod method) {
        return Order.builder().id(5L).orderNumber("ORD-5").paymentMethod(method)
                .paymentStatus(PaymentStatus.INITIATED).grandTotal(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING).build();
    }
}
