package com.stitchstyle.app.scheduler;

import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentStatus;
import com.stitchstyle.app.model.entity.ReservationStatus;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import com.stitchstyle.app.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final StockReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public ReservationExpiryJob(StockReservationRepository reservationRepository, OrderRepository orderRepository,
                                InventoryService inventoryService) {
        this.reservationRepository = reservationRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpired() {
        Instant now = Instant.now();
        var expired = reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, now);
        Set<Long> orderIds = new HashSet<>();
        expired.forEach(r -> orderIds.add(r.getOrder().getId()));
        for (Long orderId : orderIds) {
            var order = orderRepository.findById(orderId).orElse(null);
            if (order == null) continue;
            var active = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE);
            boolean allExpired = !active.isEmpty() && active.stream().allMatch(r -> !r.getExpiresAt().isAfter(now));
            if (!allExpired) continue;
            inventoryService.releaseReservations(order);
            if (order.getPaymentStatus() == PaymentStatus.INITIATED && order.getStatus() == OrderStatus.PENDING) {
                order.setPaymentStatus(PaymentStatus.EXPIRED);
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(now);
                order.setCancelReason("PAYMENT_TIMEOUT");
                orderRepository.save(order);
                log.info("prepaid order expired orderNumber={}", order.getOrderNumber());
            }
        }
    }
}
