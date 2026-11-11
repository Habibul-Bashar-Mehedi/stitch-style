package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        String orderNumber,
        String customerName,
        String customerPhone,
        String shippingAddress,
        String deliveryArea,
        String paymentMethod,
        String paymentStatus,
        String status,
        Instant placedAt,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal deliveryCharge,
        BigDecimal discountTotal,
        BigDecimal couponDiscount,
        BigDecimal grandTotal,
        String businessName,
        String businessPhone
) {
    public record Item(String productName, String sizeLabel, String sku, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) {}
}
