package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Review;
import com.stitchstyle.app.model.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, ReviewStatus status);
    List<Review> findAllByOrderByCreatedAtDesc();
    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);
    long countByProductIdAndStatus(Long productId, ReviewStatus status);
}
