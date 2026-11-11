package com.stitchstyle.app.integration.payment;

import com.stitchstyle.app.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayRegistry(java.util.List<PaymentGateway> gateways) {
        this.gateways = gateways.stream().collect(Collectors.toMap(PaymentGateway::providerCode, Function.identity()));
    }

    public PaymentGateway get(String provider) {
        PaymentGateway gateway = gateways.get(provider == null ? "" : provider.toUpperCase());
        if (gateway == null) throw new ResourceNotFoundException("Payment provider not found: " + provider);
        return gateway;
    }
}
