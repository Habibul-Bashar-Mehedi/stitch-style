package com.stitchstyle.app.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "return_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReturnRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status;
    @Column(columnDefinition = "text")
    private String reason;
    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;
    @Column(name = "refund_method")
    private String refundMethod;
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;
    @PrePersist void prePersist() { var now = Instant.now(); if (requestedAt == null) requestedAt = now; updatedAt = now; }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
