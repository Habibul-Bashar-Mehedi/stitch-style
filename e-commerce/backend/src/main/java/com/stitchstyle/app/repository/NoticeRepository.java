package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findByActiveTrueOrderByPriorityDesc();
    List<Notice> findByActiveTrueAndStartsAtBeforeAndEndsAtAfterOrderByPriorityDesc(Instant now1, Instant now2);
}
