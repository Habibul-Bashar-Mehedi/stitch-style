package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {
    List<ReturnItem> findByReturnRequestId(Long returnRequestId);
}
