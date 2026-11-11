package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.request.TrackingRequest;
import com.stitchstyle.app.dto.response.TrackingResponse;
import com.stitchstyle.app.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/orders")
public class OrderTrackingController {

    private final OrderService orderService;

    public OrderTrackingController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/track/{publicToken}")
    public TrackingResponse track(@PathVariable String publicToken) {
        return orderService.trackByToken(publicToken);
    }

    @PostMapping("/track")
    public TrackingResponse trackByPhone(@Valid @RequestBody TrackingRequest request) {
        return orderService.trackByNumberAndPhone(request.orderNumber(), request.phone());
    }
}
