package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Complaint;
import com.stitchstyle.app.model.entity.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    long countByStatusAndDueAtBefore(ComplaintStatus status, Instant now);
    long countByStatus(ComplaintStatus status);
}
