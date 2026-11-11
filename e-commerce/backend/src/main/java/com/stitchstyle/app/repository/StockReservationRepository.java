package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.ReservationStatus;
import com.stitchstyle.app.model.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);
    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant expiresAt);
    void deleteByVariantIdIn(Collection<Long> variantIds);
}
