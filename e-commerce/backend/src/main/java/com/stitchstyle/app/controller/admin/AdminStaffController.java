package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.StaffRequest;
import com.stitchstyle.app.dto.response.StaffResponse;
import com.stitchstyle.app.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final StaffService staffService;

    public AdminStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return staffService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody StaffRequest request) {
        return staffService.create(request);
    }

    @PutMapping("/{id}")
    public StaffResponse update(@PathVariable Long id, @Valid @RequestBody StaffRequest request) {
        return staffService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public StaffResponse active(@PathVariable Long id, @RequestBody java.util.Map<String, Boolean> body) {
        return staffService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }
}
