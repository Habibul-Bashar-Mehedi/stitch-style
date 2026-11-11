package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByPublicToken(String publicToken);
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByOrderNumberAndCustomerPhone(String orderNumber, String customerPhone);
    List<Order> findByUserIdOrderByPlacedAtDesc(Long userId);
    List<Order> findByPaymentStatusAndStatus(com.stitchstyle.app.model.entity.PaymentStatus paymentStatus, com.stitchstyle.app.model.entity.OrderStatus status);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.grandTotal), 0) from Order o where o.placedAt between :from and :to")
    BigDecimal sumRevenue(@org.springframework.data.repository.query.Param("from") Instant from, @org.springframework.data.repository.query.Param("to") Instant to);

    @org.springframework.data.jpa.repository.Query("select count(o) from Order o where o.placedAt between :from and :to")
    long countBetween(@org.springframework.data.repository.query.Param("from") Instant from, @org.springframework.data.repository.query.Param("to") Instant to);

    @org.springframework.data.jpa.repository.Query("select o.status, count(o) from Order o where o.placedAt between :from and :to group by o.status")
    List<Object[]> countByStatus(@org.springframework.data.repository.query.Param("from") Instant from, @org.springframework.data.repository.query.Param("to") Instant to);

    @org.springframework.data.jpa.repository.Query(value = "select count(*) from orders o where o.shipped_at is not null and o.shipped_at <= o.placed_at + interval '48 hours' and o.placed_at between :from and :to", nativeQuery = true)
    long countShippedWithin48Hours(@org.springframework.data.repository.query.Param("from") Instant from, @org.springframework.data.repository.query.Param("to") Instant to);

    @org.springframework.data.jpa.repository.Query(value = "select count(*) from orders o join delivery_areas d on d.id = o.delivery_area_id where o.delivered_at is not null and o.delivered_at <= o.placed_at + (coalesce(d.sla_days, 5) * interval '1 day') and o.placed_at between :from and :to", nativeQuery = true)
    long countDeliveredWithinSla(@org.springframework.data.repository.query.Param("from") Instant from, @org.springframework.data.repository.query.Param("to") Instant to);
}
