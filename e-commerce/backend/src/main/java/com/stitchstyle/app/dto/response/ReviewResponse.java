package com.stitchstyle.app.dto.response;

import com.stitchstyle.app.model.entity.ReviewStatus;
import java.time.Instant;

public record ReviewResponse(Long id, Long productId, String productName, String userDisplayName,
                             int rating, String comment, ReviewStatus status,
                             boolean verifiedPurchase, Instant createdAt) {}
