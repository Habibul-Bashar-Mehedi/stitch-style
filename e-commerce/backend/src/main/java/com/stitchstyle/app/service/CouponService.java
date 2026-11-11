package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.CouponRequest;
import com.stitchstyle.app.dto.response.CouponApplyResponse;
import com.stitchstyle.app.dto.response.CouponResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Coupon;
import com.stitchstyle.app.model.entity.CouponRedemption;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.CouponRedemptionRepository;
import com.stitchstyle.app.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public CouponService(CouponRepository couponRepository, CouponRedemptionRepository redemptionRepository) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
    }

    public CouponApplyResponse validate(String code, BigDecimal subtotal, User user) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new InvalidCouponException("Coupon not found."));
        validateCoupon(coupon, subtotal, user);
        BigDecimal discount = calculate(coupon, subtotal);
        return new CouponApplyResponse(coupon.getCode(), discount, "Coupon applied");
    }

    @Transactional
    public AppliedCoupon applyForOrder(String code, BigDecimal subtotal, User user) {
        Coupon coupon = couponRepository.findByCodeIgnoreCaseForUpdate(code)
                .orElseThrow(() -> new InvalidCouponException("Coupon not found."));
        validateCoupon(coupon, subtotal, user);
        BigDecimal discount = calculate(coupon, subtotal);
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);
        return new AppliedCoupon(coupon, discount);
    }

    @Transactional
    public void redeem(Order order, User user, AppliedCoupon applied) {
        redemptionRepository.save(CouponRedemption.builder()
                .coupon(applied.coupon())
                .order(order)
                .user(user)
                .amount(applied.discount())
                .build());
    }

    public List<CouponResponse> list() {
        return couponRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Coupon code already exists: " + code);
        }
        Coupon coupon = new Coupon();
        apply(coupon, request, code);
        couponRepository.save(coupon);
        return toResponse(coupon);
    }

    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        couponRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            if (!existing.getId().equals(id)) throw new DuplicateResourceException("Coupon code already exists: " + code);
        });
        apply(coupon, request, code);
        couponRepository.save(coupon);
        return toResponse(coupon);
    }

    @Transactional
    public void delete(Long id) {
        couponRepository.delete(couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id)));
    }

    private void validateCoupon(Coupon coupon, BigDecimal subtotal, User user) {
        Instant now = Instant.now();
        if (!coupon.isActive()) throw new InvalidCouponException("Coupon is not active.");
        if (coupon.getStartsAt() != null && coupon.getStartsAt().isAfter(now)) throw new InvalidCouponException("Coupon is not active yet.");
        if (coupon.getEndsAt() != null && coupon.getEndsAt().isBefore(now)) throw new InvalidCouponException("Coupon has expired.");
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) throw new InvalidCouponException("Coupon usage limit reached.");
        if (coupon.getMinSubtotal() != null && subtotal.compareTo(coupon.getMinSubtotal()) < 0) {
            throw new InvalidCouponException("Order subtotal does not meet the coupon minimum.");
        }
        if (user != null && coupon.getId() != null
                && redemptionRepository.countByCouponIdAndUserId(coupon.getId(), user.getId()) > 0) {
            throw new InvalidCouponException("You have already used this coupon.");
        }
    }

    private BigDecimal calculate(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount = "PERCENT".equalsIgnoreCase(coupon.getDiscountType())
                ? subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue();
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private void apply(Coupon coupon, CouponRequest request, String code) {
        coupon.setCode(code);
        coupon.setDiscountType(request.discountType().toUpperCase(Locale.ROOT));
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinSubtotal(request.minSubtotal());
        coupon.setMaxUses(request.maxUses());
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setActive(request.active() == null || request.active());
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMinSubtotal(), coupon.getMaxUses(), coupon.getUsedCount(), coupon.getStartsAt(),
                coupon.getEndsAt(), coupon.isActive());
    }

    public record AppliedCoupon(Coupon coupon, BigDecimal discount) {}

    public static class InvalidCouponException extends RuntimeException {
        public InvalidCouponException(String message) { super(message); }
    }
}
