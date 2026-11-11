package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.CourierRequest;
import com.stitchstyle.app.dto.request.InvoiceRequest;
import com.stitchstyle.app.dto.response.InvoiceResponse;
import com.stitchstyle.app.dto.request.OrderStatusRequest;
import com.stitchstyle.app.dto.response.AdminOrderResponse;
import com.stitchstyle.app.dto.response.PageResponse;
import com.stitchstyle.app.dto.response.TrackingResponse;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.service.InvoiceService;
import com.stitchstyle.app.service.NotificationService;
import com.stitchstyle.app.service.CourierService;
import com.stitchstyle.app.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminOrderController {

    private final OrderService orderService;
    private final InvoiceService invoiceService;
    private final NotificationService notificationService;
    private final CourierService courierService;

    public AdminOrderController(OrderService orderService, InvoiceService invoiceService,
                                NotificationService notificationService, CourierService courierService) {
        this.orderService = orderService;
        this.invoiceService = invoiceService;
        this.notificationService = notificationService;
        this.courierService = courierService;
    }

    @GetMapping
    public PageResponse<AdminOrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String phone,
                                                 @RequestParam(required = false) String orderNumber,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return orderService.adminList(status, from, to, phone, orderNumber, page, size);
    }

    @PostMapping("/invoices")
    public java.util.List<InvoiceResponse> invoices(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.invoices(request.orderIds());
    }

    @GetMapping("/{id}")
    public TrackingResponse get(@PathVariable Long id) {
        return orderService.adminGet(id);
    }

    @PatchMapping("/{id}/status")
    public TrackingResponse status(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.reason());
    }

    @PatchMapping("/{id}/courier")
    public void courier(@PathVariable Long id, @RequestBody CourierRequest request) {
        orderService.recordCourier(id, request.courierName(), request.trackingNumber());
    }

    @PostMapping("/{id}/shipment")
    public void createShipment(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        courierService.createShipment(id, body.get("courierCode"));
    }

    @PostMapping("/{id}/notifications/resend")
    public void resendNotification(@PathVariable Long id) {
        notificationService.resend(orderService.getRequired(id));
    }
}
