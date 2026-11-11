package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.ReturnRequest;
import com.stitchstyle.app.model.entity.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByOrderIdOrderByRequestedAtDesc(Long orderId);
    List<ReturnRequest> findByOrderUserIdOrderByRequestedAtDesc(Long userId);
    List<ReturnRequest> findAllByOrderByRequestedAtDesc();
    List<ReturnRequest> findByStatusOrderByRequestedAtAsc(ReturnStatus status);
    Optional<ReturnRequest> findFirstByOrderIdAndStatusNot(Long orderId, ReturnStatus status);
}
