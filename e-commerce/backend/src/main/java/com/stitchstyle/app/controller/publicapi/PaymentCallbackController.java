package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/payments")
public class PaymentCallbackController {

    private final PaymentService paymentService;

    public PaymentCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{provider}/callback")
    public ResponseEntity<Map<String, String>> callback(@PathVariable String provider,
                                                        @RequestParam Map<String, String> params) {
        paymentService.handleCallback(provider, params);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
