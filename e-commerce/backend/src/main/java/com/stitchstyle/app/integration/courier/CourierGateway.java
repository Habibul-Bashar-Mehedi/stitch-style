package com.stitchstyle.app.integration.courier;

import com.stitchstyle.app.model.entity.Order;

public interface CourierGateway {
    String code();
    String displayName();
    Shipment createShipment(Order order);

    record Shipment(String trackingNumber, String status, String labelUrl) {}
}
