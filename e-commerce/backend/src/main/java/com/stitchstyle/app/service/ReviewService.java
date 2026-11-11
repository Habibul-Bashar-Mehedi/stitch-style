package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.ReviewModerationRequest;
import com.stitchstyle.app.dto.request.ReviewRequest;
import com.stitchstyle.app.dto.response.ReviewResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.ProductRepository;
import com.stitchstyle.app.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
                         OrderItemRepository orderItemRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> approved(String productSlug) {
        Product product = productRepository.findBySlug(productSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productSlug));
        return reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(product.getId(), ReviewStatus.APPROVED)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReviewResponse submit(User user, String productSlug, ReviewRequest request) {
        if (user.getRole() != Role.CUSTOMER) {
            throw new InvalidStatusTransitionException("Staff accounts cannot submit product reviews.");
        }
        Product product = productRepository.findBySlug(productSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productSlug));
        if (reviewRepository.findByProductIdAndUserId(product.getId(), user.getId()).isPresent()) {
            throw new DuplicateResourceException("You have already reviewed this product.");
        }
        boolean verified = orderItemRepository.countDeliveredPurchases(user.getId(), product.getId()) > 0;
        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating(request.rating())
                .comment(request.comment())
                .status(ReviewStatus.PENDING)
                .verifiedPurchase(verified)
                .build();
        reviewRepository.save(review);
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> adminList() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReviewResponse moderate(Long id, ReviewModerationRequest request) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
        review.setStatus(request.status());
        review.setModeratedAt(Instant.now());
        reviewRepository.save(review);
        return toResponse(review);
    }

    private ReviewResponse toResponse(Review review) {
        String name = review.getUser().getFullName();
        String display = name == null || name.isBlank() ? "Customer"
                : name.substring(0, 1).toUpperCase() + "***";
        return new ReviewResponse(review.getId(), review.getProduct().getId(), review.getProduct().getNameI18n().getOrDefault("en", "Product"),
                display, review.getRating(), review.getComment(), review.getStatus(), review.isVerifiedPurchase(),
                review.getCreatedAt());
    }
}
