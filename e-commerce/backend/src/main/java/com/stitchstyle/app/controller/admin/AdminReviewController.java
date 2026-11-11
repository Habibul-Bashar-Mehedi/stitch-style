package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.ReviewModerationRequest;
import com.stitchstyle.app.dto.response.ReviewResponse;
import com.stitchstyle.app.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewResponse> list() { return reviewService.adminList(); }

    @PatchMapping("/{id}")
    public ReviewResponse moderate(@PathVariable Long id, @Valid @RequestBody ReviewModerationRequest request) {
        return reviewService.moderate(id, request);
    }
}
