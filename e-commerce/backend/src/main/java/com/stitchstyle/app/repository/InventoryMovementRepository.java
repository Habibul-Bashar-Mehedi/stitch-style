package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findByVariantIdOrderByCreatedAtDesc(Long variantId);
    List<InventoryMovement> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
    void deleteByVariantIdIn(Collection<Long> variantIds);
}
