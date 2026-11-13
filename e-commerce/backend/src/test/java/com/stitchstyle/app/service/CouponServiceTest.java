package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.CouponRequest;
import com.stitchstyle.app.dto.response.CouponApplyResponse;
import com.stitchstyle.app.dto.response.CouponResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Coupon;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.CouponRedemptionRepository;
import com.stitchstyle.app.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponServiceTest {

    private CouponRepository couponRepository;
    private CouponRedemptionRepository redemptionRepository;
    private CouponService service;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        redemptionRepository = mock(CouponRedemptionRepository.class);
        service = new CouponService(couponRepository, redemptionRepository);
    }

    @Test
    void percentCouponIsRoundedAndCappedAtSubtotal() {
        Coupon coupon = coupon("SAVE10", "PERCENT", new BigDecimal("10.00"));
        when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));

        CouponApplyResponse response = service.validate("SAVE10", new BigDecimal("999.99"), null);

        assertThat(response.code()).isEqualTo("SAVE10");
        assertThat(response.discount()).isEqualByComparingTo("100.00");
    }

    @Test
    void fixedCouponNeverExceedsSubtotal() {
        Coupon coupon = coupon("FLAT500", "FIXED", new BigDecimal("500.00"));
        when(couponRepository.findByCodeIgnoreCase("FLAT500")).thenReturn(Optional.of(coupon));
        when(couponRepository.findByCodeIgnoreCase("flat500")).thenReturn(Optional.of(coupon));

        assertThat(service.validate("flat500", new BigDecimal("200.00"), null).discount())
                .isEqualByComparingTo("200.00");
    }

    @Test
    void rejectsUnknownInactiveExpiredExhaustedAndBelowMinimumCoupons() {
        when(couponRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validate("NOPE", BigDecimal.TEN, null))
                .isInstanceOf(CouponService.InvalidCouponException.class)
                .hasMessageContaining("not found");

        Coupon inactive = coupon("OFF", "PERCENT", BigDecimal.TEN);
        inactive.setActive(false);
        when(couponRepository.findByCodeIgnoreCase("OFF")).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.validate("OFF", BigDecimal.TEN, null))
                .hasMessageContaining("not active");

        Coupon future = coupon("SOON", "PERCENT", BigDecimal.TEN);
        future.setStartsAt(Instant.now().plusSeconds(3600));
        when(couponRepository.findByCodeIgnoreCase("SOON")).thenReturn(Optional.of(future));
        assertThatThrownBy(() -> service.validate("SOON", BigDecimal.TEN, null))
                .hasMessageContaining("not active yet");

        Coupon expired = coupon("OLD", "PERCENT", BigDecimal.TEN);
        expired.setEndsAt(Instant.now().minusSeconds(60));
        when(couponRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.validate("OLD", BigDecimal.TEN, null))
                .hasMessageContaining("expired");

        Coupon exhausted = coupon("USED", "PERCENT", BigDecimal.TEN);
        exhausted.setMaxUses(1);
        exhausted.setUsedCount(1);
        when(couponRepository.findByCodeIgnoreCase("USED")).thenReturn(Optional.of(exhausted));
        assertThatThrownBy(() -> service.validate("USED", BigDecimal.TEN, null))
                .hasMessageContaining("usage limit");

        Coupon minimum = coupon("MIN", "PERCENT", BigDecimal.TEN);
        minimum.setMinSubtotal(new BigDecimal("1000.00"));
        when(couponRepository.findByCodeIgnoreCase("MIN")).thenReturn(Optional.of(minimum));
        assertThatThrownBy(() -> service.validate("MIN", new BigDecimal("999.99"), null))
                .hasMessageContaining("minimum");
    }

    @Test
    void rejectsSecondRedemptionByTheSameUser() {
        Coupon coupon = coupon("ONCE", "PERCENT", BigDecimal.TEN);
        coupon.setId(4L);
        User user = User.builder().id(9L).email("a@b.c").fullName("A").role(Role.CUSTOMER).active(true).build();
        when(couponRepository.findByCodeIgnoreCase("ONCE")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countByCouponIdAndUserId(4L, 9L)).thenReturn(1L);

        assertThatThrownBy(() -> service.validate("ONCE", BigDecimal.TEN, user))
                .hasMessageContaining("already used");
    }

    @Test
    void applyForOrderLocksCouponAndIncrementsUsage() {
        Coupon coupon = coupon("LOCK", "PERCENT", new BigDecimal("20.00"));
        coupon.setUsedCount(2);
        when(couponRepository.findByCodeIgnoreCaseForUpdate("LOCK")).thenReturn(Optional.of(coupon));

        CouponService.AppliedCoupon applied = service.applyForOrder("LOCK", new BigDecimal("100.00"), null);

        assertThat(applied.discount()).isEqualByComparingTo("20.00");
        assertThat(coupon.getUsedCount()).isEqualTo(3);
        verify(couponRepository).save(coupon);
    }

    @Test
    void createUppercasesCodeAndRejectsDuplicates() {
        when(couponRepository.existsByCodeIgnoreCase("WELCOME")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponResponse response = service.create(new CouponRequest(" welcome ", "percent",
                new BigDecimal("15.00"), null, null, null, null, null));

        assertThat(response.code()).isEqualTo("WELCOME");
        assertThat(response.discountType()).isEqualTo("PERCENT");
        assertThat(response.active()).isTrue();

        when(couponRepository.existsByCodeIgnoreCase("WELCOME")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CouponRequest("welcome", "PERCENT",
                BigDecimal.ONE, null, null, null, null, true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateRejectsCodeUsedByAnotherCoupon() {
        Coupon existing = coupon("ONE", "PERCENT", BigDecimal.TEN);
        existing.setId(1L);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));
        Coupon other = coupon("TWO", "PERCENT", BigDecimal.TEN);
        other.setId(2L);
        when(couponRepository.findByCodeIgnoreCase("TWO")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L, new CouponRequest("TWO", "PERCENT",
                BigDecimal.TEN, null, null, null, null, true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateAndDeleteFailForUnknownCoupon() {
        when(couponRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new CouponRequest("X", "PERCENT",
                BigDecimal.TEN, null, null, null, null, true)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(couponRepository, never()).delete(any());
    }

    @Test
    void listMapsNewestFirst() {
        when(couponRepository.findAllByOrderByIdDesc()).thenReturn(List.of(coupon("A", "PERCENT", BigDecimal.ONE)));

        assertThat(service.list()).hasSize(1).first().extracting(CouponResponse::code).isEqualTo("A");
    }

    private static Coupon coupon(String code, String type, BigDecimal value) {
        return Coupon.builder().code(code).discountType(type).discountValue(value)
                .usedCount(0).active(true).build();
    }
}
