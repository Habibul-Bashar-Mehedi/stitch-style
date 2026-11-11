package com.stitchstyle.app.controller;

import com.stitchstyle.app.dto.request.CartMergeRequest;
import com.stitchstyle.app.dto.request.ReturnQuoteRequest;
import com.stitchstyle.app.dto.request.ReturnRequestCreate;
import com.stitchstyle.app.dto.request.CartPriceRequest;
import com.stitchstyle.app.dto.response.CartResponse;
import com.stitchstyle.app.dto.response.MeResponse;
import com.stitchstyle.app.dto.response.MyOrderResponse;
import com.stitchstyle.app.dto.response.ReturnQuoteResponse;
import com.stitchstyle.app.dto.response.ReturnResponse;
import com.stitchstyle.app.dto.response.TrackingResponse;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final CurrentUserService currentUserService;
    private final CartService cartService;
    private final OrderService orderService;
    private final ReturnService returnService;

    public MeController(CurrentUserService currentUserService, CartService cartService, OrderService orderService,
                        ReturnService returnService) {
        this.currentUserService = currentUserService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.returnService = returnService;
    }

    @GetMapping("/me")
    public MeResponse me() {
        User user = currentUserService.require();
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPictureUrl(),
                user.getPhone(), user.getRole());
    }

    @GetMapping("/me/orders")
    public List<MyOrderResponse> orders() {
        User user = currentUserService.require();
        return returnService.enrichOrders(user, orderService.myOrders(user.getId()));
    }

    @GetMapping("/me/orders/{orderNumber}")
    public TrackingResponse order(@PathVariable String orderNumber) {
        return orderService.myOrder(currentUserService.require().getId(), orderNumber);
    }

    @GetMapping("/me/returns")
    public List<ReturnResponse> returns() {
        return returnService.listForUser(currentUserService.require());
    }

    @PostMapping("/me/orders/{orderNumber}/return-quote")
    public ReturnQuoteResponse returnQuote(@PathVariable String orderNumber,
                                           @Valid @RequestBody ReturnQuoteRequest request) {
        return returnService.quote(currentUserService.require(), orderNumber, request);
    }

    @PostMapping("/me/orders/{orderNumber}/returns")
    public ReturnResponse requestReturn(@PathVariable String orderNumber,
                                        @Valid @RequestBody ReturnRequestCreate request) {
        var normalized = new ReturnRequestCreate(orderNumber, request.reason(), request.items());
        return returnService.request(currentUserService.require(), normalized);
    }

    @GetMapping("/me/cart")
    public CartResponse cart(@RequestParam(required = false) String lang,
                             @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return cartService.getCart(currentUserService.require().getId(), LocaleHelper.resolve(lang, acceptLanguage));
    }

    @PutMapping("/me/cart")
    public CartResponse replace(@Valid @RequestBody CartPriceRequest request,
                                @RequestParam(required = false) String lang,
                                @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return cartService.replace(currentUserService.require().getId(), request.items(),
                LocaleHelper.resolve(lang, acceptLanguage));
    }

    @DeleteMapping("/me/cart/{variantId}")
    public CartResponse removeCartLine(@PathVariable Long variantId,
                                       @RequestParam(required = false) String lang,
                                       @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return cartService.removeLine(currentUserService.require().getId(), variantId,
                LocaleHelper.resolve(lang, acceptLanguage));
    }

    @DeleteMapping("/me/cart")
    public CartResponse clearCart(@RequestParam(required = false) String lang,
                                  @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return cartService.replace(currentUserService.require().getId(), List.of(),
                LocaleHelper.resolve(lang, acceptLanguage));
    }

    @PostMapping("/me/cart/merge")
    public CartResponse merge(@Valid @RequestBody CartMergeRequest request,
                              @RequestParam(required = false) String lang,
                              @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return cartService.merge(currentUserService.require().getId(), request.items(),
                LocaleHelper.resolve(lang, acceptLanguage));
    }
}
