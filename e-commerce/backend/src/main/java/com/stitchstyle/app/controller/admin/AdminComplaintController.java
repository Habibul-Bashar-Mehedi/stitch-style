package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.ComplaintResponseRequest;
import com.stitchstyle.app.dto.response.ComplaintResponse;
import com.stitchstyle.app.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/complaints")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminComplaintController {

    private final ComplaintService complaintService;

    public AdminComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping
    public List<ComplaintResponse> list() { return complaintService.list(); }

    @PatchMapping("/{id}")
    public ComplaintResponse respond(@PathVariable Long id, @Valid @RequestBody ComplaintResponseRequest request) {
        return complaintService.respond(id, request);
    }
}
