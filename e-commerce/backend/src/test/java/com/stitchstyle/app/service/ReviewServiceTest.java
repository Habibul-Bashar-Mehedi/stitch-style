package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.ReviewModerationRequest;
import com.stitchstyle.app.dto.request.ReviewRequest;
import com.stitchstyle.app.dto.response.ReviewResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.Review;
import com.stitchstyle.app.model.entity.ReviewStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.ProductRepository;
import com.stitchstyle.app.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private OrderItemRepository orderItemRepository;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        service = new ReviewService(reviewRepository, productRepository, orderItemRepository);
    }

    @Test
    void submitMarksDeliveredPurchasesAsVerified() {
        User customer = user(1L, Role.CUSTOMER, "Nusrat Jahan");
        Product product = product();
        when(productRepository.findBySlug("panjabi")).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(orderItemRepository.countDeliveredPurchases(1L, 10L)).thenReturn(1L);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(5L);
            review.setCreatedAt(Instant.now());
            return review;
        });

        ReviewResponse response = service.submit(customer, "panjabi", new ReviewRequest(5, "Great"));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(response.verifiedPurchase()).isTrue();
        assertThat(response.userDisplayName()).isEqualTo("N***");
    }

    @Test
    void staffCannotSubmitReviews() {
        assertThatThrownBy(() -> service.submit(user(2L, Role.ADMIN, "Admin"), "panjabi",
                new ReviewRequest(5, "Nice")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Staff accounts");
    }

    @Test
    void duplicateReviewIsRejected() {
        when(productRepository.findBySlug("panjabi")).thenReturn(Optional.of(product()));
        when(reviewRepository.findByProductIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(Review.builder().id(1L).build()));

        assertThatThrownBy(() -> service.submit(user(1L, Role.CUSTOMER, "Nusrat"), "panjabi",
                new ReviewRequest(4, "Again")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void unknownProductIsNotFound() {
        when(productRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(user(1L, Role.CUSTOMER, "Nusrat"), "missing",
                new ReviewRequest(4, "?")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approvedListsOnlyApprovedReviewsAndMasksNames() {
        Product product = product();
        when(productRepository.findBySlug("panjabi")).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(10L, ReviewStatus.APPROVED))
                .thenReturn(List.of(review(product, user(3L, Role.CUSTOMER, "  "))));

        List<ReviewResponse> result = service.approved("panjabi");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userDisplayName()).isEqualTo("Customer");
    }

    @Test
    void moderationUpdatesStatusAndTimestamp() {
        Review review = review(product(), user(1L, Role.CUSTOMER, "Nusrat"));
        review.setId(9L);
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));

        ReviewResponse response = service.moderate(9L, new ReviewModerationRequest(ReviewStatus.HIDDEN));

        assertThat(response.status()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(review.getModeratedAt()).isNotNull();
    }

    @Test
    void moderationFailsForUnknownReview() {
        when(reviewRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.moderate(9L, new ReviewModerationRequest(ReviewStatus.APPROVED)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminListMapsAllReviews() {
        when(reviewRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(review(product(),
                user(1L, Role.CUSTOMER, "Ayesha"))));

        assertThat(service.adminList()).hasSize(1);
    }

    private static Product product() {
        return Product.builder().id(10L).slug("panjabi").nameI18n(Map.of("en", "Panjabi"))
                .basePrice(java.math.BigDecimal.TEN).active(true).build();
    }

    private static Review review(Product product, User user) {
        return Review.builder().product(product).user(user).rating(5).comment("Good")
                .status(ReviewStatus.APPROVED).createdAt(Instant.now()).build();
    }

    private static User user(Long id, Role role, String name) {
        return User.builder().id(id).email(id + "@example.com").fullName(name).role(role).active(true).build();
    }
}
