package com.stitchstyle.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
        Instant from,
        Instant to,
        long orders,
        BigDecimal revenue,
        BigDecimal averageOrderValue,
        List<TopProduct> topProducts,
        List<StatusCount> statusDistribution,
        Sla sla
) {
    public record TopProduct(String productName, long quantity, BigDecimal revenue) {}
    public record StatusCount(String status, long count) {}
    public record Sla(long shippedWithin48Hours, long deliveredWithinSla, long openComplaints, long overdueComplaints) {}
}
