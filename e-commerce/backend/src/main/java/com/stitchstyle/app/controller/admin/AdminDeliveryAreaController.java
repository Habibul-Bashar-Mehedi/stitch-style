package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.DeliveryAreaRequest;
import com.stitchstyle.app.dto.response.DeliveryAreaResponse;
import com.stitchstyle.app.service.DeliveryAreaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/delivery-areas")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryAreaController {

    private final DeliveryAreaService deliveryAreaService;

    public AdminDeliveryAreaController(DeliveryAreaService deliveryAreaService) {
        this.deliveryAreaService = deliveryAreaService;
    }

    @GetMapping
    public List<DeliveryAreaResponse> list() {
        return deliveryAreaService.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryAreaResponse create(@Valid @RequestBody DeliveryAreaRequest request) {
        return deliveryAreaService.create(request);
    }

    @PutMapping("/{id}")
    public DeliveryAreaResponse update(@PathVariable Long id, @Valid @RequestBody DeliveryAreaRequest request) {
        return deliveryAreaService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        deliveryAreaService.delete(id);
    }
}
