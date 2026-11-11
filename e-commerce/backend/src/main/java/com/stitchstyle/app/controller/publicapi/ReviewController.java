package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.request.ReviewRequest;
import com.stitchstyle.app.dto.response.ReviewResponse;
import com.stitchstyle.app.service.CurrentUserService;
import com.stitchstyle.app.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/v1/public/products/{slug}/reviews")
    public List<ReviewResponse> approved(@PathVariable String slug) {
        return reviewService.approved(slug);
    }

    @PostMapping("/api/v1/me/products/{slug}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse submit(@PathVariable String slug, @Valid @RequestBody ReviewRequest request) {
        return reviewService.submit(currentUserService.require(), slug, request);
    }
}
