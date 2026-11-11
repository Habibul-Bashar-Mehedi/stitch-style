package com.stitchstyle.app.integration.payment;

import com.stitchstyle.app.model.entity.Order;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {
    String providerCode();
    PaymentInitResult initiate(Order order);
    PaymentVerification verifyCallback(Map<String, String> params);
    RefundResult refund(String providerTxnId, BigDecimal amount, String reason);

    record PaymentInitResult(String redirectUrl, String providerTxnId) {}
    record RefundResult(boolean success, String reference, String message) {}
    record PaymentVerification(boolean verified, boolean paid, String providerTxnId,
                               BigDecimal amount, String currency, String message) {}
}
