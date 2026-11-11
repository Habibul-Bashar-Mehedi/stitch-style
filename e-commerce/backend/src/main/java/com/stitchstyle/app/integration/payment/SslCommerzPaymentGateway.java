package com.stitchstyle.app.integration.payment;

import com.stitchstyle.app.exception.PaymentVerificationException;
import com.stitchstyle.app.model.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class SslCommerzPaymentGateway implements PaymentGateway {

    private final RestClient client;
    private final boolean enabled;
    private final String baseUrl;
    private final String storeId;
    private final String storePassword;
    private final String callbackBaseUrl;
    private final String callbackSecret;

    public SslCommerzPaymentGateway(RestClient.Builder builder,
                                    @Value("${app.payments.sslcommerz.enabled:false}") boolean enabled,
                                    @Value("${app.payments.sslcommerz.base-url:https://sandbox.sslcommerz.com}") String baseUrl,
                                    @Value("${app.payments.sslcommerz.store-id:}") String storeId,
                                    @Value("${app.payments.sslcommerz.store-password:}") String storePassword,
                                    @Value("${app.payments.callback-base-url:http://localhost:18080}") String callbackBaseUrl,
                                    @Value("${app.payments.callback-secret:dev-callback-secret}") String callbackSecret) {
        this.client = builder.build();
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.storeId = storeId;
        this.storePassword = storePassword;
        this.callbackBaseUrl = callbackBaseUrl;
        this.callbackSecret = callbackSecret;
    }

    @Override
    public String providerCode() { return "SSLCOMMERZ"; }

    @Override
    public PaymentInitResult initiate(Order order) {
        if (!enabled) {
            String txn = "SSL-" + UUID.randomUUID();
            return new PaymentInitResult("/pay/stub?provider=SSLCOMMERZ&order=" + order.getOrderNumber() + "&txn=" + txn, txn);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("store_id", storeId);
        form.add("store_passwd", storePassword);
        form.add("total_amount", order.getGrandTotal().toPlainString());
        form.add("currency", "BDT");
        form.add("tran_id", order.getOrderNumber());
        form.add("success_url", callbackBaseUrl + "/api/v1/public/payments/SSLCOMMERZ/callback?status=VALID&order=" + order.getOrderNumber());
        form.add("fail_url", callbackBaseUrl + "/api/v1/public/payments/SSLCOMMERZ/callback?status=FAILED&order=" + order.getOrderNumber());
        form.add("cancel_url", callbackBaseUrl + "/api/v1/public/payments/SSLCOMMERZ/callback?status=CANCELLED&order=" + order.getOrderNumber());
        form.add("cus_name", order.getCustomerName());
        form.add("cus_phone", order.getCustomerPhone());
        form.add("cus_email", "customer@example.com");
        form.add("cus_add1", order.getShippingAddress());
        form.add("cus_city", order.getDeliveryArea().getDistrict() == null ? "Dhaka" : order.getDeliveryArea().getDistrict());
        form.add("cus_country", "Bangladesh");
        form.add("shipping_method", "NO");
        form.add("product_name", "Stitch & Style order");
        form.add("product_category", "Fashion");
        form.add("product_profile", "physical-goods");
        Map<String, Object> response = client.post()
                .uri(baseUrl + "/gwprocess/v4/api.php")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null) throw new PaymentVerificationException("Empty response from SSLCommerz.");
        String url = str(response, "GatewayPageURL");
        String txn = str(response, "tran_id");
        if (url == null) throw new PaymentVerificationException("SSLCommerz did not return a GatewayPageURL.");
        return new PaymentInitResult(url, txn == null ? order.getOrderNumber() : txn);
    }

    @Override
    public PaymentVerification verifyCallback(Map<String, String> params) {
        String valId = params.get("val_id");
        if (!enabled || valId == null) {
            boolean verified = callbackSecret.equals(params.get("signature")) || valId != null;
            boolean paid = verified && "VALID".equalsIgnoreCase(params.get("status"));
            return new PaymentVerification(verified, paid, params.get("providerTxnId"), parse(params.get("amount")),
                    params.getOrDefault("currency", "BDT"), verified ? "stub verified" : "invalid val_id/signature");
        }
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/validator/api/validationserverAPI.php")
                .queryParam("val_id", valId)
                .queryParam("store_id", storeId)
                .queryParam("store_passwd", storePassword)
                .queryParam("format", "json")
                .build().toUriString();
        Map<String, Object> response = client.get().uri(uri).retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null) throw new PaymentVerificationException("Empty validation response from SSLCommerz.");
        String status = str(response, "status");
        boolean paid = "VALID".equalsIgnoreCase(status) || "VALIDATED".equalsIgnoreCase(status);
        return new PaymentVerification(true, paid, str(response, "bank_tran_id"), parse(str(response, "amount")),
                str(response, "currency") == null ? "BDT" : str(response, "currency"), status);
    }

    @Override
    public RefundResult refund(String providerTxnId, BigDecimal amount, String reason) {
        if (!enabled) return new RefundResult(true, providerTxnId + "-REFUND", "stub refund accepted");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("store_id", storeId);
        form.add("store_passwd", storePassword);
        form.add("bank_tran_id", providerTxnId);
        form.add("refund_amount", amount.toPlainString());
        form.add("refund_remarks", reason);
        form.add("format", "json");
        Map<String, Object> response = client.post()
                .uri(baseUrl + "/validator/api/merchantTransIDvalidationAPI.php")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        String status = response == null ? null : str(response, "status");
        return new RefundResult("SUCCESS".equalsIgnoreCase(status) || "SUCCESSFUL".equalsIgnoreCase(status),
                response == null ? null : str(response, "refund_ref_id"), status == null ? "SSLCommerz refund response" : status);
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
