package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    Optional<MediaAsset> findByStorageKey(String storageKey);
    Page<MediaAsset> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
