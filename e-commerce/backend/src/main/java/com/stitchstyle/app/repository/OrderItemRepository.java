package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    long countByProductId(Long productId);

    @Query("select oi.productNameSnapshot, sum(oi.quantity), sum(oi.lineTotal) from OrderItem oi where oi.order.placedAt between :from and :to group by oi.productNameSnapshot order by sum(oi.quantity) desc")
    List<Object[]> topProducts(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("select count(oi) from OrderItem oi where oi.order.user.id = :userId and oi.product.id = :productId and oi.order.status = com.stitchstyle.app.model.entity.OrderStatus.DELIVERED")
    long countDeliveredPurchases(@Param("userId") Long userId, @Param("productId") Long productId);
}
