package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.ReturnCondition;
import com.stitchstyle.app.model.entity.ReturnStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnResponse(
        Long id,
        String orderNumber,
        ReturnStatus status,
        String reason,
        String resolutionNote,
        String refundMethod,
        BigDecimal refundAmount,
        Instant requestedAt,
        Instant resolvedAt,
        ReturnQuoteResponse quote,
        List<Item> items
) {
    public record Item(Long id, Long orderItemId, String productName, String sizeLabel,
                       int quantity, ReturnCondition condition, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
