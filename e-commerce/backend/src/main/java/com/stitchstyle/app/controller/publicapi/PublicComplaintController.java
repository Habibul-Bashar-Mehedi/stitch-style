package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.request.ComplaintRequest;
import com.stitchstyle.app.dto.response.ComplaintResponse;
import com.stitchstyle.app.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/complaints")
public class PublicComplaintController {

    private final ComplaintService complaintService;

    public PublicComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintResponse create(@Valid @RequestBody ComplaintRequest request) {
        return complaintService.create(request);
    }
}
