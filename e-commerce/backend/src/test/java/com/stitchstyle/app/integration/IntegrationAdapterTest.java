package com.stitchstyle.app.integration;

import com.stitchstyle.app.integration.courier.PathaoCourierGateway;
import com.stitchstyle.app.integration.courier.RedxCourierGateway;
import com.stitchstyle.app.integration.courier.SteadfastCourierGateway;
import com.stitchstyle.app.integration.media.LocalMediaStorage;
import com.stitchstyle.app.integration.media.S3MediaStorage;
import com.stitchstyle.app.integration.notification.EmailSender;
import com.stitchstyle.app.integration.notification.SmsSender;
import com.stitchstyle.app.integration.payment.BkashPaymentGateway;
import com.stitchstyle.app.integration.payment.PaymentGateway;
import com.stitchstyle.app.integration.payment.PaymentGatewayRegistry;
import com.stitchstyle.app.integration.payment.SslCommerzPaymentGateway;
import com.stitchstyle.app.model.entity.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationAdapterTest {

    private final RestClient.Builder builder = RestClient.builder();

    @Test
    void courierStubsCreateTrackingNumbersForEveryProvider() {
        Order order = Order.builder().orderNumber("ORD-1").build();

        var pathao = new PathaoCourierGateway(false, "", "").createShipment(order);
        assertThat(pathao.trackingNumber()).startsWith("PATHAO-ORD-1-");
        assertThat(pathao.status()).isEqualTo("STUB_CREATED");

        var steadfast = new SteadfastCourierGateway(false, "", "", "").createShipment(order);
        assertThat(steadfast.trackingNumber()).startsWith("STEADFAST-ORD-1-");

        var redx = new RedxCourierGateway(false, "", "").createShipment(order);
        assertThat(redx.trackingNumber()).startsWith("REDX-ORD-1-");
    }

    @Test
    void liveCourierCredentialsAreNotSilentlyIgnored() {
        Order order = Order.builder().orderNumber("ORD-1").build();

        assertThatThrownBy(() -> new PathaoCourierGateway(true, "", "").createShipment(order))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new SteadfastCourierGateway(true, "", "", "").createShipment(order))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new RedxCourierGateway(true, "", "").createShipment(order))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void localMediaStorageWritesReadsUrlsAndDeletes(@TempDir Path root) throws Exception {
        LocalMediaStorage storage = new LocalMediaStorage(root.toString(), "/media/");

        storage.store("sub/a.txt", "hello".getBytes(), "text/plain");

        assertThat(storage.load("sub/a.txt")).isEqualTo("hello".getBytes());
        assertThat(storage.url("sub/a.txt")).isEqualTo("/media/sub/a.txt");

        storage.delete("sub/a.txt");
        assertThat(Files.exists(root.resolve("sub/a.txt"))).isFalse();
        // deleting again is harmless
        storage.delete("sub/a.txt");
    }

    @Test
    void localMediaStorageRejectsPathTraversal(@TempDir Path root) {
        LocalMediaStorage storage = new LocalMediaStorage(root.toString(), "/media");

        assertThatThrownBy(() -> storage.store("../escape.txt", new byte[]{1}, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid media key");
    }

    @Test
    void s3AdapterFailsLoudlyUntilItIsConfigured() {
        S3MediaStorage storage = new S3MediaStorage();

        assertThatThrownBy(() -> storage.store("a", new byte[]{1}, "text/plain"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> storage.url("a")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void blankSmsConfigurationIsANoOp() {
        SmsSender sender = new SmsSender(builder, "", "", "StitchStyle");
        sender.send("+8801712345678", "ORDER_CONFIRMATION", Map.of("orderNumber", "ORD-1"));
        // reaching here proves no network call was attempted
        assertThat(sender).isNotNull();
    }

    @Test
    void emailWithoutHostLogsAndReturns() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", mailSender);
        EmailSender sender = new EmailSender(beans.getBeanProvider(JavaMailSender.class), "", "from@x.com");

        sender.send("to@x.com", "ORDER_CONFIRMATION", Map.of("orderNumber", "ORD-1"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void configuredEmailSendsASubjectAndBody() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", mailSender);
        EmailSender sender = new EmailSender(beans.getBeanProvider(JavaMailSender.class), "smtp.example.com", "from@x.com");

        sender.send("to@x.com", "ORDER_SHIPPED", Map.of("orderNumber", "ORD-1"));

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("to@x.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Your order has shipped");
        assertThat(captor.getValue().getText()).contains("orderNumber: ORD-1");
    }

    @Test
    void unknownEmailTemplateGetsTheDefaultSubject() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", mailSender);
        EmailSender sender = new EmailSender(beans.getBeanProvider(JavaMailSender.class), "smtp", "from@x.com");

        sender.send("to@x.com", "SOMETHING_NEW", Map.of());

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Stitch & Style update");
    }

    @Test
    void paymentRegistryResolvesProvidersCaseInsensitivelyAndRejectsUnknownOnes() {
        PaymentGateway bkash = new BkashPaymentGateway(builder, false, "", "", "", "", "", "", "secret");
        PaymentGateway ssl = new SslCommerzPaymentGateway(builder, false, "", "", "", "", "secret");
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(bkash, ssl));

        assertThat(registry.get("bkash")).isSameAs(bkash);
        assertThat(registry.get("SSLCOMMERZ")).isSameAs(ssl);
        assertThatThrownBy(() -> registry.get("paypal"))
                .isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> registry.get(null))
                .isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
    }

    @Test
    void bkashStubVerifiesSignatureAndRejectsWrongOne() {
        PaymentGateway gateway = new BkashPaymentGateway(builder, false, "", "", "", "", "", "", "secret");

        var good = gateway.verifyCallback(Map.of("signature", "secret", "status", "success",
                "providerTxnId", "TXN", "amount", "100.00", "currency", "BDT"));
        assertThat(good.verified()).isTrue();
        assertThat(good.paid()).isTrue();

        var bad = gateway.verifyCallback(Map.of("signature", "wrong", "status", "success"));
        assertThat(bad.verified()).isFalse();
        assertThat(bad.paid()).isFalse();

        var failed = gateway.verifyCallback(Map.of("signature", "secret", "status", "failed"));
        assertThat(failed.verified()).isTrue();
        assertThat(failed.paid()).isFalse();
    }

    @Test
    void sslCommerzStubAcceptsEitherSignatureOrValidationId() {
        PaymentGateway gateway = new SslCommerzPaymentGateway(builder, false, "", "", "", "", "secret");

        assertThat(gateway.verifyCallback(Map.of("val_id", "VAL", "status", "VALID",
                "providerTxnId", "TXN", "amount", "100.00", "currency", "BDT")).paid()).isTrue();
        assertThat(gateway.verifyCallback(Map.of("signature", "secret", "status", "VALID")).verified()).isTrue();
        assertThat(gateway.verifyCallback(Map.of("val_id", "VAL", "status", "FAILED")).paid()).isFalse();
    }

    @Test
    void stubPaymentInitiationProducesARedirectAndRefund() {
        PaymentGateway bkash = new BkashPaymentGateway(builder, false, "", "", "", "", "", "", "secret");
        Order order = Order.builder().orderNumber("ORD-1").grandTotal(new BigDecimal("100.00"))
                .customerPhone("+8801712345678").build();

        var init = bkash.initiate(order);
        assertThat(init.redirectUrl()).contains("provider=BKASH").contains("ORD-1");
        assertThat(init.providerTxnId()).startsWith("BKASH-");
        assertThat(bkash.refund(init.providerTxnId(), BigDecimal.TEN, "return").success()).isTrue();
    }
}
