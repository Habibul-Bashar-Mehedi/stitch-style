package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.AnalyticsResponse;
import com.stitchstyle.app.model.entity.ComplaintStatus;
import com.stitchstyle.app.repository.ComplaintRepository;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ComplaintRepository complaintRepository;

    public AnalyticsService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                            ComplaintRepository complaintRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.complaintRepository = complaintRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse summary(Instant from, Instant to, boolean includeRevenue) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        long orders = orderRepository.countBetween(start, end);
        BigDecimal revenue = includeRevenue ? orderRepository.sumRevenue(start, end) : null;
        BigDecimal average = includeRevenue && orders > 0
                ? revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP) : null;
        List<AnalyticsResponse.TopProduct> top = includeRevenue
                ? orderItemRepository.topProducts(start, end, PageRequest.of(0, 5)).stream()
                    .map(row -> new AnalyticsResponse.TopProduct(String.valueOf(row[0]),
                            ((Number) row[1]).longValue(), (BigDecimal) row[2]))
                    .toList()
                : List.of();
        List<AnalyticsResponse.StatusCount> statuses = orderRepository.countByStatus(start, end).stream()
                .map(row -> new AnalyticsResponse.StatusCount(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
        AnalyticsResponse.Sla sla = new AnalyticsResponse.Sla(
                orderRepository.countShippedWithin48Hours(start, end),
                orderRepository.countDeliveredWithinSla(start, end),
                complaintRepository.countByStatus(ComplaintStatus.OPEN),
                complaintRepository.countByStatusAndDueAtBefore(ComplaintStatus.OPEN, Instant.now()));
        return new AnalyticsResponse(start, end, orders, revenue, average, top, statuses, sla);
    }
}
