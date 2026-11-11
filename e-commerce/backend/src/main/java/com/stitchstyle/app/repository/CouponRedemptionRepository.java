package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    Optional<CouponRedemption> findByOrderId(Long orderId);
    long countByCouponIdAndUserId(Long couponId, Long userId);
}
