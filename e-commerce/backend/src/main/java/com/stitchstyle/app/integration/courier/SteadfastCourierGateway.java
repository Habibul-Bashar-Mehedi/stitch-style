package com.stitchstyle.app.integration.courier;

import com.stitchstyle.app.model.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SteadfastCourierGateway implements CourierGateway {
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String secretKey;

    public SteadfastCourierGateway(@Value("${app.couriers.steadfast.enabled:false}") boolean enabled,
                                   @Value("${app.couriers.steadfast.base-url:https://portal.packzy.com/api/v1}") String baseUrl,
                                   @Value("${app.couriers.steadfast.api-key:}") String apiKey,
                                   @Value("${app.couriers.steadfast.secret-key:}") String secretKey) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
    }

    @Override public String code() { return "STEADFAST"; }
    @Override public String displayName() { return "Steadfast"; }

    @Override
    public Shipment createShipment(Order order) {
        if (!enabled) {
            return new Shipment("STEADFAST-" + order.getOrderNumber() + "-" + UUID.randomUUID().toString().substring(0, 6),
                    "STUB_CREATED", null);
        }
        throw new UnsupportedOperationException("Steadfast live credentials are not configured in this environment.");
    }
}
