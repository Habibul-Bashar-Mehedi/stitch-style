package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.AnalyticsResponse;
import com.stitchstyle.app.model.entity.ComplaintStatus;
import com.stitchstyle.app.repository.ComplaintRepository;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ComplaintRepository complaintRepository;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        complaintRepository = mock(ComplaintRepository.class);
        service = new AnalyticsService(orderRepository, orderItemRepository, complaintRepository);
    }

    @Test
    void summaryComputesRevenueAverageTopProductsAndSla() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T00:00:00Z");
        when(orderRepository.countBetween(from, to)).thenReturn(4L);
        when(orderRepository.sumRevenue(from, to)).thenReturn(new BigDecimal("1000.00"));
        when(orderItemRepository.topProducts(any(), any(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{"Panjabi", 3L, new BigDecimal("900.00")}));
        when(orderRepository.countByStatus(from, to)).thenReturn(List.<Object[]>of(new Object[]{"PENDING", 2L}));
        when(orderRepository.countShippedWithin48Hours(from, to)).thenReturn(3L);
        when(orderRepository.countDeliveredWithinSla(from, to)).thenReturn(2L);
        when(complaintRepository.countByStatus(ComplaintStatus.OPEN)).thenReturn(5L);
        when(complaintRepository.countByStatusAndDueAtBefore(any(), any())).thenReturn(1L);

        AnalyticsResponse response = service.summary(from, to, true);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
        assertThat(response.orders()).isEqualTo(4L);
        assertThat(response.revenue()).isEqualByComparingTo("1000.00");
        assertThat(response.averageOrderValue()).isEqualByComparingTo("250.00");
        assertThat(response.topProducts()).singleElement().satisfies(product -> {
            assertThat(product.productName()).isEqualTo("Panjabi");
            assertThat(product.quantity()).isEqualTo(3L);
            assertThat(product.revenue()).isEqualByComparingTo("900.00");
        });
        assertThat(response.statusDistribution()).singleElement()
                .satisfies(status -> assertThat(status.count()).isEqualTo(2L));
        assertThat(response.sla().shippedWithin48Hours()).isEqualTo(3L);
        assertThat(response.sla().deliveredWithinSla()).isEqualTo(2L);
        assertThat(response.sla().openComplaints()).isEqualTo(5L);
        assertThat(response.sla().overdueComplaints()).isEqualTo(1L);
    }

    @Test
    void withoutRevenueTopProductsAndRevenueAreSkipped() {
        when(orderRepository.countBetween(any(), any())).thenReturn(0L);
        when(orderRepository.countByStatus(any(), any())).thenReturn(List.of());

        AnalyticsResponse response = service.summary(null, null, false);

        assertThat(response.revenue()).isNull();
        assertThat(response.averageOrderValue()).isNull();
        assertThat(response.topProducts()).isEmpty();
        verify(orderRepository, never()).sumRevenue(any(), any());
        verify(orderItemRepository, never()).topProducts(any(), any(), any());
    }

    @Test
    void zeroOrdersMeansNoAverage() {
        when(orderRepository.countBetween(any(), any())).thenReturn(0L);
        when(orderRepository.sumRevenue(any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.countByStatus(any(), any())).thenReturn(List.of());

        assertThat(service.summary(null, null, true).averageOrderValue()).isNull();
    }
}
