package com.stitchstyle.app.integration.courier;

import com.stitchstyle.app.model.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedxCourierGateway implements CourierGateway {
    private final boolean enabled;
    private final String baseUrl;
    private final String token;

    public RedxCourierGateway(@Value("${app.couriers.redx.enabled:false}") boolean enabled,
                              @Value("${app.couriers.redx.base-url:https://openapi.redx.com.bd/v1.0.0-beta}") String baseUrl,
                              @Value("${app.couriers.redx.token:}") String token) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override public String code() { return "REDX"; }
    @Override public String displayName() { return "RedX"; }

    @Override
    public Shipment createShipment(Order order) {
        if (!enabled) {
            return new Shipment("REDX-" + order.getOrderNumber() + "-" + UUID.randomUUID().toString().substring(0, 6),
                    "STUB_CREATED", null);
        }
        throw new UnsupportedOperationException("RedX live credentials are not configured in this environment.");
    }
}
