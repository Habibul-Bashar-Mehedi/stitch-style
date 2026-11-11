package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.DeliveryArea;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryAreaRepository extends JpaRepository<DeliveryArea, Long> {
    List<DeliveryArea> findByActiveTrueOrderByDisplayOrderAsc();
    List<DeliveryArea> findAllByOrderByDisplayOrderAsc();
}
