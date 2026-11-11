package com.stitchstyle.app.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "reviews_product_user_uk", columnNames = {"product_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private int rating;
    @Column(columnDefinition = "text")
    private String comment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;
    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "moderated_at")
    private Instant moderatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id")
    private User moderator;
    @PrePersist void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = ReviewStatus.PENDING;
    }
}
