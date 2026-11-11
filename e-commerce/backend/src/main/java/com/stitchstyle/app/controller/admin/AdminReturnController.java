package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.RefundRequest;
import com.stitchstyle.app.dto.request.ReturnStatusRequest;
import com.stitchstyle.app.dto.response.ReturnResponse;
import com.stitchstyle.app.service.ReturnService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/returns")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminReturnController {

    private final ReturnService returnService;

    public AdminReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    public List<ReturnResponse> list() {
        return returnService.list();
    }

    @GetMapping("/{id}")
    public ReturnResponse get(@PathVariable Long id) {
        return returnService.get(id);
    }

    @PatchMapping("/{id}")
    public ReturnResponse advance(@PathVariable Long id, @Valid @RequestBody ReturnStatusRequest request) {
        return returnService.advance(id, request);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnResponse refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        return returnService.refund(id, request);
    }
}
