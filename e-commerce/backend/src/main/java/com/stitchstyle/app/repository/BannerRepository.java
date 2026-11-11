package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findByActiveTrueOrderByDisplayOrderAsc();
    List<Banner> findByActiveTrueAndStartsAtBeforeAndEndsAtAfterOrderByDisplayOrderAsc(Instant now1, Instant now2);
    long countByImageMediaId(Long imageMediaId);
}
