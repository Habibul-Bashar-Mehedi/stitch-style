package com.stitchstyle.app.integration.payment;

import com.stitchstyle.app.exception.PaymentVerificationException;
import com.stitchstyle.app.model.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class BkashPaymentGateway implements PaymentGateway {

    private final RestClient client;
    private final boolean enabled;
    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final String username;
    private final String password;
    private final String callbackBaseUrl;
    private final String callbackSecret;

    public BkashPaymentGateway(RestClient.Builder builder,
                               @Value("${app.payments.bkash.enabled:false}") boolean enabled,
                               @Value("${app.payments.bkash.base-url:https://tokenized.sandbox.bka.sh/v1.2.0-beta}") String baseUrl,
                               @Value("${app.payments.bkash.app-key:}") String appKey,
                               @Value("${app.payments.bkash.app-secret:}") String appSecret,
                               @Value("${app.payments.bkash.username:}") String username,
                               @Value("${app.payments.bkash.password:}") String password,
                               @Value("${app.payments.callback-base-url:http://localhost:18080}") String callbackBaseUrl,
                               @Value("${app.payments.callback-secret:dev-callback-secret}") String callbackSecret) {
        this.client = builder.build();
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.username = username;
        this.password = password;
        this.callbackBaseUrl = callbackBaseUrl;
        this.callbackSecret = callbackSecret;
    }

    @Override
    public String providerCode() { return "BKASH"; }

    @Override
    public PaymentInitResult initiate(Order order) {
        if (!enabled) {
            String txn = "BKASH-" + UUID.randomUUID();
            return new PaymentInitResult("/pay/stub?provider=BKASH&order=" + order.getOrderNumber() + "&txn=" + txn, txn);
        }
        String token = grantToken();
        Map<String, Object> response = post(baseUrl + "/tokenized/checkout/create", token, Map.of(
                "mode", "0011",
                "payerReference", order.getCustomerPhone(),
                "callbackURL", callbackBaseUrl + "/api/v1/public/payments/BKASH/callback",
                "amount", order.getGrandTotal().toPlainString(),
                "currency", "BDT",
                "intent", "sale",
                "merchantInvoiceNumber", order.getOrderNumber()));
        String paymentId = str(response, "paymentID");
        String redirect = str(response, "bkashURL");
        if (paymentId == null || redirect == null) {
            throw new PaymentVerificationException("bKash did not return a payment id and redirect URL.");
        }
        return new PaymentInitResult(redirect, paymentId);
    }

    @Override
    public PaymentVerification verifyCallback(Map<String, String> params) {
        String paymentId = params.get("paymentID");
        if (!enabled || paymentId == null) {
            boolean verified = callbackSecret.equals(params.get("signature"));
            boolean paid = verified && "success".equalsIgnoreCase(params.get("status"));
            return new PaymentVerification(verified, paid, params.get("providerTxnId"), parse(params.get("amount")),
                    params.getOrDefault("currency", "BDT"), verified ? "stub verified" : "invalid signature");
        }
        String token = grantToken();
        Map<String, Object> response = post(baseUrl + "/tokenized/checkout/execute", token, Map.of("paymentID", paymentId));
        String status = str(response, "transactionStatus");
        String txn = str(response, "trxID");
        boolean paid = "Completed".equalsIgnoreCase(status);
        return new PaymentVerification(true, paid, txn == null ? paymentId : txn,
                parse(str(response, "amount")), str(response, "currency") == null ? "BDT" : str(response, "currency"), status);
    }

    @Override
    public RefundResult refund(String providerTxnId, BigDecimal amount, String reason) {
        if (!enabled) return new RefundResult(true, providerTxnId + "-REFUND", "stub refund accepted");
        String token = grantToken();
        Map<String, Object> response = post(baseUrl + "/tokenized/checkout/payment/refund", token, Map.of(
                "paymentID", providerTxnId,
                "amount", amount.toPlainString(),
                "currency", "BDT",
                "reason", reason));
        String status = str(response, "transactionStatus");
        return new RefundResult("Completed".equalsIgnoreCase(status), str(response, "refundTrxID"),
                status == null ? "bKash refund response" : status);
    }

    private String grantToken() {
        Map<String, Object> response = client.post()
                .uri(baseUrl + "/tokenized/checkout/token/grant")
                .contentType(MediaType.APPLICATION_JSON)
                .header("username", username)
                .header("password", password)
                .body(Map.of("app_key", appKey, "app_secret", appSecret))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        String token = response == null ? null : str(response, "id_token");
        if (token == null) throw new PaymentVerificationException("bKash token grant failed.");
        return token;
    }

    private Map<String, Object> post(String url, String token, Map<String, Object> body) {
        Map<String, Object> response = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", token)
                .header("X-APP-Key", appKey)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null) throw new PaymentVerificationException("Empty response from bKash.");
        return response;
    }

    private String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal parse(String value) {
        try { return value == null ? BigDecimal.ZERO : new BigDecimal(value); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
