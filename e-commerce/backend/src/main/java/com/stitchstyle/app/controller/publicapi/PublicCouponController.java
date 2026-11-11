package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.request.CouponValidateRequest;
import com.stitchstyle.app.dto.response.CouponApplyResponse;
import com.stitchstyle.app.service.CouponService;
import com.stitchstyle.app.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/coupons")
public class PublicCouponController {

    private final CouponService couponService;
    private final CurrentUserService currentUserService;

    public PublicCouponController(CouponService couponService, CurrentUserService currentUserService) {
        this.couponService = couponService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/validate")
    public CouponApplyResponse validate(@Valid @RequestBody CouponValidateRequest request) {
        return couponService.validate(request.code(), request.subtotal(), currentUserService.optional().orElse(null));
    }
}
