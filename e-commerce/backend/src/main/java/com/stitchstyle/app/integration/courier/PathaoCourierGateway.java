package com.stitchstyle.app.integration.courier;

import com.stitchstyle.app.model.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PathaoCourierGateway implements CourierGateway {
    private final boolean enabled;
    private final String baseUrl;
    private final String token;

    public PathaoCourierGateway(@Value("${app.couriers.pathao.enabled:false}") boolean enabled,
                                @Value("${app.couriers.pathao.base-url:https://api-hermes.pathao.com}") String baseUrl,
                                @Value("${app.couriers.pathao.token:}") String token) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override public String code() { return "PATHAO"; }
    @Override public String displayName() { return "Pathao"; }

    @Override
    public Shipment createShipment(Order order) {
        if (!enabled) {
            return new Shipment("PATHAO-" + order.getOrderNumber() + "-" + UUID.randomUUID().toString().substring(0, 6),
                    "STUB_CREATED", null);
        }
        // Live Pathao merchant onboarding supplies the store id and credentials; the
        // adapter boundary is deliberately explicit so production credentials stay secret.
        throw new UnsupportedOperationException("Pathao live credentials are not configured in this environment.");
    }
}
