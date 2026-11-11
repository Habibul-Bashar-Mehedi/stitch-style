package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.NoticeRequest;
import com.stitchstyle.app.dto.response.NoticeResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Notice;
import com.stitchstyle.app.repository.NoticeRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NoticeService {

    private final NoticeRepository repository;
    private final CacheEvictionService cacheEvictionService;

    public NoticeService(NoticeRepository repository, CacheEvictionService cacheEvictionService) {
        this.repository = repository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Cacheable(cacheNames = CacheNames.NOTICES, key = "'active'")
    public List<NoticeResponse> active() {
        Instant now = Instant.now();
        return repository.findByActiveTrueOrderByPriorityDesc().stream()
                .filter(n -> (n.getStartsAt() == null || !n.getStartsAt().isAfter(now))
                        && (n.getEndsAt() == null || n.getEndsAt().isAfter(now)))
                .map(this::toResponse).toList();
    }

    public List<NoticeResponse> adminList() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public NoticeResponse create(NoticeRequest request) {
        Notice notice = new Notice();
        apply(notice, request);
        repository.save(notice);
        evict();
        return toResponse(notice);
    }

    @Transactional
    public NoticeResponse update(Long id, NoticeRequest request) {
        Notice notice = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        apply(notice, request);
        repository.save(notice);
        evict();
        return toResponse(notice);
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        repository.delete(notice);
        evict();
    }

    private void apply(Notice notice, NoticeRequest request) {
        notice.setTitle(request.title());
        notice.setBody(request.body());
        notice.setPriority(request.priority() == null ? 0 : request.priority());
        notice.setActive(request.active() == null || request.active());
        notice.setStartsAt(request.startsAt());
        notice.setEndsAt(request.endsAt());
    }

    private void evict() {
        cacheEvictionService.evictAfterCommit(CacheNames.NOTICES);
    }

    private NoticeResponse toResponse(Notice notice) {
        return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getBody(), notice.getPriority(),
                notice.getStartsAt(), notice.getEndsAt());
    }
}
