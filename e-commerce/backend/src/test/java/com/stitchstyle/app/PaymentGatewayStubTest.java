package com.stitchstyle.app;

import com.stitchstyle.app.integration.payment.BkashPaymentGateway;
import com.stitchstyle.app.integration.payment.SslCommerzPaymentGateway;
import com.stitchstyle.app.model.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayStubTest {

    @Test
    void bkashStubInitiatesVerifiesAndRefunds() {
        var gateway = new BkashPaymentGateway(RestClient.builder(), false, "", "", "", "", "", "", "secret");
        Order order = Order.builder().orderNumber("ORD-TEST").grandTotal(new BigDecimal("100.00"))
                .customerPhone("+8801712345678").build();
        var init = gateway.initiate(order);
        assertThat(init.redirectUrl()).contains("/pay/stub?provider=BKASH");
        var verify = gateway.verifyCallback(Map.of("signature", "secret", "status", "success",
                "providerTxnId", "TXN-1", "amount", "100.00", "currency", "BDT"));
        assertThat(verify.verified()).isTrue();
        assertThat(verify.paid()).isTrue();
        assertThat(gateway.refund("TXN-1", new BigDecimal("100.00"), "return").success()).isTrue();
    }

    @Test
    void sslCommerzStubInitiatesAndVerifies() {
        var gateway = new SslCommerzPaymentGateway(RestClient.builder(), false, "", "", "", "", "secret");
        Order order = Order.builder().orderNumber("ORD-TEST").grandTotal(new BigDecimal("100.00"))
                .customerName("Test").customerPhone("+8801712345678").build();
        var init = gateway.initiate(order);
        assertThat(init.redirectUrl()).contains("/pay/stub?provider=SSLCOMMERZ");
        var verify = gateway.verifyCallback(Map.of("signature", "secret", "status", "VALID",
                "val_id", "VAL-1", "amount", "100.00", "currency", "BDT"));
        assertThat(verify.verified()).isTrue();
        assertThat(verify.paid()).isTrue();
    }
}
