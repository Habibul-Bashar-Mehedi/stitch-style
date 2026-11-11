package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.request.CartPriceRequest;
import com.stitchstyle.app.dto.response.CartResponse;
import com.stitchstyle.app.service.CartService;
import com.stitchstyle.app.service.LocaleHelper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/cart/price")
    public CartResponse price(@Valid @RequestBody CartPriceRequest request,
                              @RequestParam(required = false) String lang,
                              @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return cartService.price(request, LocaleHelper.resolve(lang, acceptLanguage));
    }
}
