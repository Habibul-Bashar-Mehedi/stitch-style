package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.InvoiceResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderItem;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final SiteSettingService siteSettingService = mock(SiteSettingService.class);
    private final InvoiceService service = new InvoiceService(orderRepository, orderItemRepository, siteSettingService);

    @Test
    void invoiceMapsSnapshotsAndBusinessSettings() {
        Order order = order();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(OrderItem.builder()
                .productNameSnapshot("Panjabi").sizeLabelSnapshot("M").skuSnapshot("SKU-1")
                .quantity(2).unitPriceSnapshot(new BigDecimal("500.00"))
                .lineTotal(new BigDecimal("1000.00")).build()));
        when(siteSettingService.get("business.name", "Stitch & Style")).thenReturn("Stitch & Style");
        when(siteSettingService.get("business.phone", "")).thenReturn("01700000000");

        InvoiceResponse invoice = service.invoice(1L);

        assertThat(invoice.orderNumber()).isEqualTo("ORD-1");
        assertThat(invoice.items()).singleElement()
                .satisfies(item -> assertThat(item.productName()).isEqualTo("Panjabi"));
        assertThat(invoice.businessName()).isEqualTo("Stitch & Style");
        assertThat(invoice.businessPhone()).isEqualTo("01700000000");
    }

    @Test
    void invoicesMapsEachIdAndFailsForAMissingOne() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order()));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        assertThat(service.invoices(List.of(1L))).hasSize(1);

        when(orderRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.invoices(List.of(1L, 2L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Order order() {
        return Order.builder().id(1L).orderNumber("ORD-1").customerName("Nusrat")
                .customerPhone("+8801712345678").shippingAddress("Dhaka")
                .deliveryArea(DeliveryArea.builder().id(1L).name("Dhaka").build())
                .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.UNPAID)
                .status(OrderStatus.PENDING).placedAt(Instant.now())
                .subtotal(new BigDecimal("1000.00")).deliveryCharge(new BigDecimal("60.00"))
                .discountTotal(BigDecimal.ZERO).couponDiscount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("1060.00")).build();
    }
}
