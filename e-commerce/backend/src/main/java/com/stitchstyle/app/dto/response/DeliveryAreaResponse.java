package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;

public record DeliveryAreaResponse(Long id, String name, String district, BigDecimal deliveryCharge,
                                   BigDecimal freeShippingThreshold, Integer slaDays, int displayOrder) implements java.io.Serializable {}
