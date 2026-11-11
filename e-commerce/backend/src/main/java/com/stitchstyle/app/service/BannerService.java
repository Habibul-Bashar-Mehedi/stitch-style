package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.BannerRequest;
import com.stitchstyle.app.dto.response.BannerResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Banner;
import com.stitchstyle.app.repository.BannerRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BannerService {

    private final BannerRepository repository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaService mediaService;
    private final CacheEvictionService cacheEvictionService;

    public BannerService(BannerRepository repository, MediaAssetRepository mediaAssetRepository,
                         MediaService mediaService, CacheEvictionService cacheEvictionService) {
        this.repository = repository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaService = mediaService;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.BANNERS, key = "'active'")
    public List<BannerResponse> active() {
        Instant now = Instant.now();
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .filter(b -> (b.getStartsAt() == null || !b.getStartsAt().isAfter(now))
                        && (b.getEndsAt() == null || b.getEndsAt().isAfter(now)))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> adminList() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public BannerResponse create(BannerRequest request) {
        Banner banner = new Banner();
        apply(banner, request);
        repository.save(banner);
        evict();
        return toResponse(banner);
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest request) {
        Banner banner = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found: " + id));
        apply(banner, request);
        repository.save(banner);
        evict();
        return toResponse(banner);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found: " + id));
        repository.delete(banner);
        evict();
    }

    private void apply(Banner banner, BannerRequest request) {
        banner.setTitle(request.title());
        banner.setImageMedia(mediaAssetRepository.findById(request.imageMediaId())
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + request.imageMediaId())));
        banner.setLinkUrl(request.linkUrl());
        banner.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        banner.setActive(request.active() == null || request.active());
        banner.setStartsAt(request.startsAt());
        banner.setEndsAt(request.endsAt());
    }

    private void evict() {
        cacheEvictionService.evictAfterCommit(CacheNames.BANNERS);
    }

    private BannerResponse toResponse(Banner banner) {
        return new BannerResponse(banner.getId(), banner.getTitle(), mediaService.url(banner.getImageMedia()),
                banner.getLinkUrl(), banner.getDisplayOrder(), banner.getStartsAt(), banner.getEndsAt());
    }
}
