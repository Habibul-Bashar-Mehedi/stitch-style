package com.stitchstyle.app.service;

import com.stitchstyle.app.exception.PaymentVerificationException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.integration.payment.PaymentGateway;
import com.stitchstyle.app.integration.payment.PaymentGatewayRegistry;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.Payment;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentGatewayRegistry registry;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public PaymentService(PaymentGatewayRegistry registry, PaymentRepository paymentRepository,
                          OrderRepository orderRepository, InventoryService inventoryService) {
        this.registry = registry;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public String initiate(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.COD) return null;
        PaymentGateway gateway = registry.get(order.getPaymentMethod().name());
        PaymentGateway.PaymentInitResult result = gateway.initiate(order);
        Payment payment = Payment.builder()
                .order(order)
                .provider(gateway.providerCode())
                .providerTxnId(result.providerTxnId())
                .amount(order.getGrandTotal())
                .currency("BDT")
                .status("INITIATED")
                .rawResponse(Map.of("redirectUrl", result.redirectUrl()))
                .build();
        paymentRepository.save(payment);
        return result.redirectUrl();
    }

    @Transactional
    public void handleCallback(String provider, Map<String, String> params) {
        PaymentGateway gateway = registry.get(provider);
        PaymentGateway.PaymentVerification verification = gateway.verifyCallback(params);
        if (!verification.verified()) {
            throw new PaymentVerificationException("The payment callback signature is invalid.");
        }
        Payment payment = paymentRepository.findByProviderAndProviderTxnId(provider.toUpperCase(), verification.providerTxnId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for callback."));
        if ("PAID".equals(payment.getStatus())) {
            return;
        }
        if (verification.amount().compareTo(payment.getAmount()) != 0
                || !verification.currency().equalsIgnoreCase(payment.getCurrency())) {
            throw new PaymentVerificationException("Payment amount or currency does not match the order.");
        }
        Order order = payment.getOrder();
        if (verification.paid()) {
            inventoryService.commitReservations(order);
            payment.setStatus("PAID");
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setStatus(com.stitchstyle.app.model.entity.OrderStatus.PENDING);
        } else {
            inventoryService.releaseReservations(order);
            payment.setStatus("FAILED");
            order.setPaymentStatus(PaymentStatus.FAILED);
        }
        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    @Transactional
    public String refund(Order order, BigDecimal amount, String reason) {
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            return "MANUAL_COD";
        }
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment == null) return "MANUAL";
        PaymentGateway gateway = registry.get(payment.getProvider());
        PaymentGateway.RefundResult result = gateway.refund(payment.getProviderTxnId(), amount, reason);
        if (!result.success()) {
            throw new PaymentVerificationException("Refund failed: " + result.message());
        }
        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);
        return payment.getProvider();
    }
}
