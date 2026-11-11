package com.stitchstyle.app.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(name = "discount_type", nullable = false)
    private String discountType;
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;
    @Column(name = "min_subtotal", precision = 12, scale = 2)
    private BigDecimal minSubtotal;
    @Column(name = "max_uses")
    private Integer maxUses;
    @Column(name = "used_count", nullable = false)
    private int usedCount;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(nullable = false)
    private boolean active = true;
}
