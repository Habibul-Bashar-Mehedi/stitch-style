package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Coupon c where lower(c.code) = lower(:code)")
    Optional<Coupon> findByCodeIgnoreCaseForUpdate(@org.springframework.data.repository.query.Param("code") String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Coupon> findAllByOrderByIdDesc();
}
