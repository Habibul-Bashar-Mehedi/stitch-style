package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.SettingRequest;
import com.stitchstyle.app.dto.response.MediaResponse;
import com.stitchstyle.app.exception.MediaValidationException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.integration.media.MediaStorage;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.BannerRepository;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.ProductImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    private MediaAssetRepository repository;
    private ProductImageRepository productImageRepository;
    private CategoryRepository categoryRepository;
    private BannerRepository bannerRepository;
    private MediaStorage storage;
    private MediaService service;
    private final User uploader = User.builder().id(1L).email("admin@x.com").fullName("Admin")
            .role(Role.ADMIN).active(true).build();

    @BeforeEach
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        productImageRepository = mock(ProductImageRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        bannerRepository = mock(BannerRepository.class);
        storage = mock(MediaStorage.class);
        service = new MediaService(repository, productImageRepository, categoryRepository, bannerRepository,
                storage, 10_000_000, 6000);
        when(storage.url(anyString())).thenAnswer(invocation -> "/media/" + invocation.getArgument(0));
    }

    @Test
    void emptyFilesAreRejected() {
        assertThatThrownBy(() -> service.upload(null, uploader)).isInstanceOf(MediaValidationException.class);
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.png", "image/png", new byte[0]),
                uploader)).isInstanceOf(MediaValidationException.class);
    }

    @Test
    void filesAboveTheLimitAreRejected() {
        service = new MediaService(repository, productImageRepository, categoryRepository, bannerRepository,
                storage, 10, 6000);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.png", "image/png",
                new byte[20]), uploader))
                .isInstanceOf(MediaValidationException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void svgAndUnsupportedTypesAreRejected() {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.svg", "image/svg+xml",
                "<svg/>".getBytes()), uploader))
                .isInstanceOf(MediaValidationException.class);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.txt", "text/plain",
                "hello".getBytes()), uploader))
                .isInstanceOf(MediaValidationException.class);
    }

    @Test
    void validPngIsStoredInspectedAndPersisted() throws Exception {
        byte[] png = png(10, 20);
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(7L);
            return asset;
        });

        MediaResponse response = service.upload(new MockMultipartFile("file", "photo.png", "image/png", png), uploader);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.width()).isEqualTo(10);
        assertThat(response.height()).isEqualTo(20);
        assertThat(response.url()).startsWith("/media/");
        assertThat(response.byteSize()).isPositive();
        verify(storage).store(anyString(), any(byte[].class), eq("image/png"));
        verify(repository).save(any(MediaAsset.class));
    }

    @Test
    void mislabelledBinaryIsRejected() {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "fake.png", "image/png",
                "not an image".getBytes()), uploader))
                .isInstanceOf(MediaValidationException.class)
                .hasMessageContaining("readable image");
    }

    @Test
    void webpBytesAreAcceptedWithoutPixelInspection() {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaResponse response = service.upload(new MockMultipartFile("file", "a.webp", "image/webp",
                new byte[]{1, 2, 3}), uploader);

        assertThat(response.width()).isNull();
        assertThat(response.height()).isNull();
    }

    @Test
    void contentTypeIsInferredFromTheFilenameWhenMissing() throws Exception {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upload(new MockMultipartFile("file", "photo.jpg", null, png(5, 5)), uploader);

        assertThat(response.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void deletingReferencedMediaIsRejected() {
        MediaAsset asset = MediaAsset.builder().id(1L).storageKey("a.png").build();
        when(repository.findById(1L)).thenReturn(Optional.of(asset));
        when(productImageRepository.countByMediaAssetId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(MediaValidationException.class)
                .hasMessageContaining("still referenced");
        verify(storage, never()).delete(anyString());
    }

    @Test
    void unreferencedMediaIsDeletedFromStorageAndDatabase() {
        MediaAsset asset = MediaAsset.builder().id(1L).storageKey("a.png").build();
        when(repository.findById(1L)).thenReturn(Optional.of(asset));

        service.delete(1L);

        verify(storage).delete("a.png");
        verify(repository).delete(asset);
    }

    @Test
    void deletingUnknownMediaFails() {
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listMapsAPage() {
        when(repository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(MediaAsset.builder().id(1L).storageKey("a.png").build())));

        assertThat(service.list(PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    @Test
    void urlAndResponseHandleNullAssets() {
        assertThat(service.url(null)).isNull();
        assertThatThrownBy(() -> service.getRequired(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
