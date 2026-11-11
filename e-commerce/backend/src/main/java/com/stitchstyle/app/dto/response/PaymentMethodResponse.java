package com.stitchstyle.app.dto.response;

public record PaymentMethodResponse(String code, String label, boolean enabled) implements java.io.Serializable {}
