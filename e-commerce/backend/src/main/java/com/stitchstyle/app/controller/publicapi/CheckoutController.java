package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.response.OrderResponse;
import com.stitchstyle.app.service.CurrentUserService;
import com.stitchstyle.app.service.LocaleHelper;
import com.stitchstyle.app.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/public")
public class CheckoutController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public CheckoutController(OrderService orderService, CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody OrderRequest request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @CookieValue(value = "guest_id", required = false) String guestId,
                                               @RequestParam(required = false) String lang,
                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
                                               HttpServletRequest httpRequest) {
        var user = currentUserService.optional().orElse(null);
        if (guestId == null || guestId.isBlank()) {
            guestId = java.util.UUID.randomUUID().toString();
        }
        OrderResponse response = orderService.place(request, user, guestId, idempotencyKey,
                LocaleHelper.resolve(lang, acceptLanguage));
        if (response.replayed()) {
            return ResponseEntity.ok().header("Idempotency-Replayed", "true").body(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/public/orders/track/" + response.publicToken()))
                .body(response);
    }
}
