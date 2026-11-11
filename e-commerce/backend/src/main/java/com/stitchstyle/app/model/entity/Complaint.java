package com.stitchstyle.app.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "complaints")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String phone;
    @Column(name = "order_number")
    private String orderNumber;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Column(name = "responded_at")
    private Instant respondedAt;
    @Column(columnDefinition = "text")
    private String response;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist void prePersist() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (dueAt == null) dueAt = now.plusSeconds(72 * 3600);
        if (status == null) status = ComplaintStatus.OPEN;
    }
}
