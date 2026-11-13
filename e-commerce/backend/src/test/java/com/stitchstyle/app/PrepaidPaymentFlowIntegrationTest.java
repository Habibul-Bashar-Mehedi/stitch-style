package com.stitchstyle.app;

import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.Payment;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.ReservationStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.PaymentRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrepaidPaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired StockReservationRepository reservationRepository;

    @Test
    void bkashCheckoutReservesStockAndCallbackCommitsIt() throws Exception {
        Fixture fixture = fixture("commit");
        String key = "it-prepay-" + suffix() + "-0001";

        MvcResult placed = place(fixture, key, "BKASH");
        JsonNode order = json(placed);
        String orderNumber = order.get("orderNumber").asText();
        String token = order.get("publicToken").asText();
        String redirect = order.get("redirectUrl").asText();
        String txn = redirect.replaceAll(".*[?&]txn=([^&]+).*", "$1");
        assertThat(redirect).contains("provider=BKASH");

        Order stored = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(stored.getPaymentStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(variantRepository.findById(fixture.variant.getId()).orElseThrow().getReservedQuantity())
                .isEqualTo(1);
        assertThat(variantRepository.findById(fixture.variant.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(3);
        assertThat(reservationRepository.findByOrderIdAndStatus(stored.getId(), ReservationStatus.ACTIVE))
                .hasSize(1);

        callback(txn, "success", new BigDecimal(order.get("grandTotal").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        Order paid = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PENDING);
        Payment payment = paymentRepository.findByOrderId(paid.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("PAID");
        ProductVariant variant = variantRepository.findById(fixture.variant.getId()).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(2);
        assertThat(variant.getReservedQuantity()).isZero();

        // A replayed callback must not decrement stock a second time.
        callback(txn, "success", new BigDecimal(order.get("grandTotal").asText())).andExpect(status().isOk());
        assertThat(variantRepository.findById(fixture.variant.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(2);

        mvc.perform(get("/api/v1/public/orders/track/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void failedPaymentCallbackReleasesReservations() throws Exception {
        Fixture fixture = fixture("fail");
        MvcResult placed = place(fixture, "it-prepay-" + suffix() + "-0002", "BKASH");
        JsonNode order = json(placed);
        String txn = order.get("redirectUrl").asText().replaceAll(".*[?&]txn=([^&]+).*", "$1");

        callback(txn, "failed", new BigDecimal(order.get("grandTotal").asText())).andExpect(status().isOk());

        Order stored = orderRepository.findByOrderNumber(order.get("orderNumber").asText()).orElseThrow();
        assertThat(stored.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        ProductVariant variant = variantRepository.findById(fixture.variant.getId()).orElseThrow();
        assertThat(variant.getStockQuantity()).isEqualTo(3);
        assertThat(variant.getReservedQuantity()).isZero();
        assertThat(reservationRepository.findByOrderIdAndStatus(stored.getId(), ReservationStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void callbackWithTheWrongSignatureIsRejected() throws Exception {
        Fixture fixture = fixture("signature");
        MvcResult placed = place(fixture, "it-prepay-" + suffix() + "-0003", "BKASH");
        JsonNode order = json(placed);
        String txn = order.get("redirectUrl").asText().replaceAll(".*[?&]txn=([^&]+).*", "$1");

        mvc.perform(post("/api/v1/public/payments/BKASH/callback")
                        .param("signature", "wrong")
                        .param("status", "success")
                        .param("providerTxnId", txn)
                        .param("amount", order.get("grandTotal").asText())
                        .param("currency", "BDT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        assertThat(orderRepository.findByOrderNumber(order.get("orderNumber").asText()).orElseThrow()
                .getPaymentStatus()).isEqualTo(PaymentStatus.INITIATED);
    }

    @Test
    void aDifferentAmountCannotBeConfirmed() throws Exception {
        Fixture fixture = fixture("amount");
        MvcResult placed = place(fixture, "it-prepay-" + suffix() + "-0004", "BKASH");
        JsonNode order = json(placed);
        String txn = order.get("redirectUrl").asText().replaceAll(".*[?&]txn=([^&]+).*", "$1");

        callback(txn, "success", new BigDecimal("1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));
    }

    @Test
    void identicalRequestsReplayAndChangedBodiesReuseProtection() throws Exception {
        Fixture fixture = fixture("idempotent");
        String key = "it-prepay-" + suffix() + "-0005";

        MvcResult first = place(fixture, key, "COD");
        String orderNumber = json(first).get("orderNumber").asText();

        mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-" + fixture.tag))
                        .content(payload(fixture, "COD")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.replayed").value(true));

        String changed = payload(fixture, "COD").replace("\"quantity\":1", "\"quantity\":2");
        mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-" + fixture.tag))
                        .content(changed))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void missingOrMalformedIdempotencyKeysAreRejected() throws Exception {
        Fixture fixture = fixture("keys");

        mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-keys"))
                        .content(payload(fixture, "COD")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "short")
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-keys"))
                        .content(payload(fixture, "COD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void ordersWithoutCsrfAreRejected() throws Exception {
        Fixture fixture = fixture("csrf");
        mvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "it-prepay-" + suffix() + "-0006")
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-csrf"))
                        .content(payload(fixture, "COD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void insufficientStockAndInvalidCouponsAreRejected() throws Exception {
        Fixture fixture = fixture("errors");
        fixture.variant.setStockQuantity(1);
        variantRepository.save(fixture.variant);

        String key = "it-prepay-" + suffix() + "-0007";
        place(fixture, key, "COD");

        mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key + "x")
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-errors"))
                        .content(payload(fixture, "COD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        ProductVariant refreshed = variantRepository.findById(fixture.variant.getId()).orElseThrow();
        refreshed.setStockQuantity(5);
        variantRepository.saveAndFlush(refreshed);
        mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key + "y")
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-errors"))
                        .content(payload(fixture, "COD").replace("\"couponCode\":null",
                                "\"couponCode\":\"NO-SUCH-COUPON\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUPON"));
    }

    @Test
    void usersCanTrackOrdersByNumberAndPhone() throws Exception {
        Fixture fixture = fixture("track");
        MvcResult placed = place(fixture, "it-prepay-" + suffix() + "-0008", "COD");
        String orderNumber = json(placed).get("orderNumber").asText();

        mvc.perform(post("/api/v1/public/orders/track").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"" + orderNumber + "\",\"phone\":\"01712345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber));

        mvc.perform(post("/api/v1/public/orders/track").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"" + orderNumber + "\",\"phone\":\"01999999999\"}"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions callback(String txn, String status, BigDecimal amount) throws Exception {
        return mvc.perform(post("/api/v1/public/payments/BKASH/callback")
                .param("signature", "it-callback-secret")
                .param("status", status)
                .param("providerTxnId", txn)
                .param("amount", amount.toPlainString())
                .param("currency", "BDT"));
    }

    private MvcResult place(Fixture fixture, String key, String method) throws Exception {
        return mvc.perform(post("/api/v1/public/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .cookie(new jakarta.servlet.http.Cookie("guest_id", "it-guest-" + fixture.tag))
                        .content(payload(fixture, method)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String payload(Fixture fixture, String method) {
        return """
                {"customer":{"name":"It Customer","phone":"01712345678"},
                 "shippingAddress":"House 1, Road 1, Dhaka","deliveryAreaId":%d,
                 "paymentMethod":"%s","couponCode":null,
                 "items":[{"variantId":%d,"quantity":1,"selected":true}]}
                """.formatted(fixture.area.getId(), method, fixture.variant.getId());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Fixture fixture(String tag) {
        String unique = suffix();
        Category category = ensureCategory("it-pay-cat-" + unique, "Pay Category " + unique, null, false);
        Product product = ensureProduct(category, "it-pay-product-" + unique, "Pay Product " + unique,
                "পেমেন্ট", new BigDecimal("1200.00"), null);
        ProductVariant variant = ensureVariant(product, "M", "IT-PAY-" + unique.toUpperCase(), 3, 0);
        DeliveryArea area = ensureDeliveryArea("IT Pay Area " + unique, new BigDecimal("60.00"), null);
        return new Fixture(tag + unique, variant, area);
    }

    private record Fixture(String tag, ProductVariant variant, DeliveryArea area) {
    }
}
