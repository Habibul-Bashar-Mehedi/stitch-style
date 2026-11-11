package com.stitchstyle.app.service;

import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.integration.courier.CourierGateway;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourierService {

    private final Map<String, CourierGateway> gateways;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public CourierService(List<CourierGateway> gateways, OrderRepository orderRepository, AuditService auditService) {
        this.gateways = gateways.stream().collect(Collectors.toMap(CourierGateway::code, Function.identity()));
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Order createShipment(Long orderId, String courierCode) {
        CourierGateway gateway = gateways.get(courierCode == null ? "" : courierCode.toUpperCase());
        if (gateway == null) throw new ResourceNotFoundException("Courier not found: " + courierCode);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        CourierGateway.Shipment shipment = gateway.createShipment(order);
        order.setCourierName(gateway.displayName());
        order.setTrackingNumber(shipment.trackingNumber());
        orderRepository.save(order);
        auditService.record(com.stitchstyle.app.model.entity.AuditAction.UPDATE, "ORDER", order.getId(),
                Map.of("courier", String.valueOf(order.getCourierName())),
                Map.of("courier", gateway.displayName(), "trackingNumber", shipment.trackingNumber()));
        return order;
    }
}
