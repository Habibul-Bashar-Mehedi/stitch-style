package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.response.MediaResponse;
import com.stitchstyle.app.exception.MediaValidationException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.integration.media.MediaStorage;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.BannerRepository;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.ProductImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/avif");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/avif", "avif");

    private final MediaAssetRepository repository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final BannerRepository bannerRepository;
    private final MediaStorage storage;
    private final long maxBytes;
    private final int maxDimension;

    public MediaService(MediaAssetRepository repository, ProductImageRepository productImageRepository,
                        CategoryRepository categoryRepository, BannerRepository bannerRepository,
                        MediaStorage storage,
                        @Value("${app.media.max-bytes:10485760}") long maxBytes,
                        @Value("${app.media.max-dimension:6000}") int maxDimension) {
        this.repository = repository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.bannerRepository = bannerRepository;
        this.storage = storage;
        this.maxBytes = maxBytes;
        this.maxDimension = maxDimension;
    }

    @Transactional
    public MediaResponse upload(MultipartFile file, User uploader) {
        if (file == null || file.isEmpty()) throw new MediaValidationException("Upload a non-empty image file.");
        if (file.getSize() > maxBytes) throw new MediaValidationException("Image exceeds the 10 MB limit.");

        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String contentType = normalizeContentType(file.getContentType(), filename);
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new MediaValidationException("Allowed image types are JPEG, PNG, WebP and AVIF. SVG is rejected.");
        }
        if (filename.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            throw new MediaValidationException("SVG uploads are not allowed.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new MediaValidationException("Could not read the uploaded file.");
        }

        ImageInfo imageInfo = inspectAndNormalize(bytes, contentType);
        String key = UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        storage.store(key, imageInfo.bytes(), contentType);

        MediaAsset asset = MediaAsset.builder()
                .storageKey(key)
                .originalFilename(filename)
                .mimeType(contentType)
                .byteSize(imageInfo.bytes().length)
                .width(imageInfo.width())
                .height(imageInfo.height())
                .checksumSha256(sha256(imageInfo.bytes()))
                .createdBy(uploader)
                .build();
        repository.save(asset);
        return toResponse(asset);
    }

    public Page<MediaResponse> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    public MediaAsset getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + id));
    }

    public String url(MediaAsset asset) {
        return asset == null ? null : storage.url(asset.getStorageKey());
    }

    @Transactional
    public void delete(Long id) {
        MediaAsset asset = getRequired(id);
        if (productImageRepository.countByMediaAssetId(id) > 0
                || categoryRepository.countByImageMediaId(id) > 0
                || bannerRepository.countByImageMediaId(id) > 0) {
            throw new MediaValidationException("Media is still referenced by a product, category or banner.");
        }
        storage.delete(asset.getStorageKey());
        repository.delete(asset);
    }

    public MediaResponse toResponse(MediaAsset asset) {
        return new MediaResponse(asset.getId(), storage.url(asset.getStorageKey()), asset.getOriginalFilename(),
                asset.getMimeType(), asset.getByteSize(), asset.getWidth(), asset.getHeight(), asset.getCreatedAt());
    }

    private String normalizeContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".avif")) return "image/avif";
        return null;
    }

    private ImageInfo inspectAndNormalize(byte[] bytes, String contentType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                // WebP/AVIF readers are not guaranteed in the JDK; retain the bytes and skip pixel checks.
                if ("image/webp".equals(contentType) || "image/avif".equals(contentType)) {
                    return new ImageInfo(bytes, null, null);
                }
                throw new MediaValidationException("The uploaded file is not a readable image.");
            }
            if (image.getWidth() > maxDimension || image.getHeight() > maxDimension) {
                throw new MediaValidationException("Image dimensions exceed 6000 x 6000 px.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String format = switch (contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                default -> null;
            };
            if (format != null && ImageIO.write(image, format, out)) {
                return new ImageInfo(out.toByteArray(), image.getWidth(), image.getHeight());
            }
            return new ImageInfo(bytes, image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new MediaValidationException("The uploaded file could not be decoded.");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record ImageInfo(byte[] bytes, Integer width, Integer height) {}
}
