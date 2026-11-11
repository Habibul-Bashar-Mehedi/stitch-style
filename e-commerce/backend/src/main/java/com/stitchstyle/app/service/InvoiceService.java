package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.InvoiceResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderItem;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InvoiceService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SiteSettingService siteSettingService;

    public InvoiceService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                          SiteSettingService siteSettingService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.siteSettingService = siteSettingService;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> invoices(List<Long> orderIds) {
        return orderIds.stream().map(this::invoice).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse invoice(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        List<InvoiceResponse.Item> items = orderItemRepository.findByOrderId(orderId).stream()
                .map(item -> new InvoiceResponse.Item(item.getProductNameSnapshot(), item.getSizeLabelSnapshot(),
                        item.getSkuSnapshot(), item.getQuantity(), item.getUnitPriceSnapshot(), item.getLineTotal()))
                .toList();
        return new InvoiceResponse(order.getOrderNumber(), order.getCustomerName(), order.getCustomerPhone(),
                order.getShippingAddress(), order.getDeliveryArea().getName(), order.getPaymentMethod().name(),
                order.getPaymentStatus().name(), order.getStatus().name(), order.getPlacedAt(), items,
                order.getSubtotal(), order.getDeliveryCharge(), order.getDiscountTotal(), order.getCouponDiscount(),
                order.getGrandTotal(), siteSettingService.get("business.name", "Stitch & Style"),
                siteSettingService.get("business.phone", ""));
    }
}
