package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.dto.request.BannerRequest;
import com.stitchstyle.app.dto.request.NoticeRequest;
import com.stitchstyle.app.model.entity.Banner;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.Notice;
import com.stitchstyle.app.repository.BannerRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.NoticeRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BannerAndNoticeServiceTest {

    private final BannerRepository bannerRepository = mock(BannerRepository.class);
    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final BannerService bannerService = new BannerService(bannerRepository, mediaAssetRepository,
            mediaService, mock(CacheEvictionService.class));

    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private final NoticeService noticeService = new NoticeService(noticeRepository, mock(CacheEvictionService.class));

    @Test
    void activeBannersAreFilteredByScheduleWindow() {
        Instant now = Instant.now();
        MediaAsset media = MediaAsset.builder().id(1L).storageKey("a.png").build();
        when(bannerRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(
                Banner.builder().id(1L).title("Live").imageMedia(media).active(true).build(),
                Banner.builder().id(2L).title("Future").imageMedia(media).startsAt(now.plusSeconds(3600)).build(),
                Banner.builder().id(3L).title("Expired").imageMedia(media).endsAt(now.minusSeconds(60)).build()));
        when(mediaService.url(media)).thenReturn("/media/a.png");

        assertThat(bannerService.active()).extracting(response -> response.title()).containsExactly("Live");
    }

    @Test
    void bannerCreateRequiresExistingMediaAndEvicts() {
        when(mediaAssetRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.create(new BannerRequest("Sale", 1L, "/sale",
                0, true, null, null)))
                .isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
    }

    @Test
    void bannerCreatePersistsAndMaps() {
        MediaAsset media = MediaAsset.builder().id(1L).storageKey("banner.png").build();
        when(mediaAssetRepository.findById(1L)).thenReturn(Optional.of(media));
        when(bannerRepository.save(any(Banner.class))).thenAnswer(invocation -> {
            Banner banner = invocation.getArgument(0);
            banner.setId(4L);
            return banner;
        });
        when(mediaService.url(media)).thenReturn("/media/banner.png");

        var response = bannerService.create(new BannerRequest("Sale", 1L, "/sale", 2, true, null, null));

        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.imageUrl()).isEqualTo("/media/banner.png");
        assertThat(response.displayOrder()).isEqualTo(2);
    }

    @Test
    void bannerUpdateAndDeleteFailForUnknownId() {
        when(bannerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bannerService.update(9L, new BannerRequest("x", 1L, null, 0, true,
                null, null))).isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> bannerService.delete(9L))
                .isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
    }

    @Test
    void bannerUpdateAndDeleteWork() {
        MediaAsset media = MediaAsset.builder().id(1L).storageKey("banner.png").build();
        Banner banner = Banner.builder().id(5L).title("Old").imageMedia(media).active(true).build();
        when(bannerRepository.findById(5L)).thenReturn(Optional.of(banner));
        when(mediaAssetRepository.findById(1L)).thenReturn(Optional.of(media));
        when(mediaService.url(media)).thenReturn("/media/banner.png");

        var response = bannerService.update(5L, new BannerRequest("New", 1L, "/new", 1, false, null, null));

        assertThat(response.title()).isEqualTo("New");
        verify(bannerRepository).save(banner);

        bannerService.delete(5L);
        verify(bannerRepository).delete(banner);
    }

    @Test
    void adminBannerListReturnsEverything() {
        when(bannerRepository.findAll()).thenReturn(List.of());
        assertThat(bannerService.adminList()).isEmpty();
    }

    @Test
    void activeNoticesAreFilteredByPriorityAndWindow() {
        Instant now = Instant.now();
        when(noticeRepository.findByActiveTrueOrderByPriorityDesc()).thenReturn(List.of(
                Notice.builder().id(1L).title("Live").body("b").priority(5).active(true).build(),
                Notice.builder().id(2L).title("Future").body("b").priority(9)
                        .startsAt(now.plusSeconds(3600)).build(),
                Notice.builder().id(3L).title("Expired").body("b").priority(9)
                        .endsAt(now.minusSeconds(1)).build()));

        assertThat(noticeService.active()).extracting(response -> response.title()).containsExactly("Live");
    }

    @Test
    void noticeCrudAppliesDefaults() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> {
            Notice notice = invocation.getArgument(0);
            notice.setId(1L);
            return notice;
        });

        var created = noticeService.create(new NoticeRequest("Title", "Body", null, null, null, null));
        assertThat(created.priority()).isZero();

        Notice stored = Notice.builder().id(1L).title("T").body("B").priority(0).active(true).build();
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(stored));
        var updated = noticeService.update(1L, new NoticeRequest("New", "Body", 7, false, null, null));
        assertThat(updated.title()).isEqualTo("New");
        assertThat(stored.getPriority()).isEqualTo(7);
        assertThat(stored.isActive()).isFalse();

        noticeService.delete(1L);
        verify(noticeRepository).delete(stored);
    }

    @Test
    void noticeUpdateAndDeleteFailForUnknownId() {
        when(noticeRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.update(8L, new NoticeRequest("x", "y", 0, true, null, null)))
                .isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> noticeService.delete(8L))
                .isInstanceOf(com.stitchstyle.app.exception.ResourceNotFoundException.class);
    }

    @Test
    void noticeAdminListReturnsEverything() {
        when(noticeRepository.findAll()).thenReturn(List.of());
        assertThat(noticeService.adminList()).isEmpty();
    }
}
