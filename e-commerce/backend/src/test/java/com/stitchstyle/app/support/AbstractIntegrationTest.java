package com.stitchstyle.app.support;

import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductImage;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.SizeType;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.DeliveryAreaRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.ProductImageRepository;
import com.stitchstyle.app.repository.ProductRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.SiteSettingRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Shared Testcontainers-backed application context for the integration suite.
 * One PostgreSQL container is started per JVM and reused by every subclass, so
 * the schema is migrated once rather than per test class.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:latest");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.rate-limit.enabled", () -> "false");
        registry.add("app.dev-tools.enabled", () -> "true");
        registry.add("app.payments.callback-secret", () -> "it-callback-secret");
        registry.add("app.notifications.sms.url", () -> "");
        registry.add("spring.mail.host", () -> "");
        registry.add("app.media.local-root",
                () -> System.getProperty("java.io.tmpdir") + "/stitchstyle-it-media");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected CacheManager cacheManager;

    @Autowired protected CategoryRepository categoryRepository;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected ProductVariantRepository variantRepository;
    @Autowired protected ProductImageRepository productImageRepository;
    @Autowired protected MediaAssetRepository mediaAssetRepository;
    @Autowired protected DeliveryAreaRepository deliveryAreaRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected SiteSettingRepository siteSettingRepository;

    @BeforeEach
    void clearApplicationCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    protected static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected User ensureUser(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() ->
                userRepository.save(User.builder().fullName(role.name().toLowerCase() + " " + email)
                        .email(email).role(role).active(true).build()));
    }

    protected Category ensureCategory(String slug, String name, Category parent, boolean showOnHome) {
        return categoryRepository.findBySlug(slug).orElseGet(() -> {
            Category category = new Category();
            category.setName(name);
            category.setSlug(slug);
            category.setParent(parent);
            category.setDepth(parent == null ? 0 : parent.getDepth() + 1);
            category.setSizeType(SizeType.LETTER);
            category.setActive(true);
            category.setShowOnHome(showOnHome);
            category.setShowInNavbar(true);
            return categoryRepository.save(category);
        });
    }

    protected Product ensureProduct(Category category, String slug, String nameEn, String nameBn,
                                    BigDecimal basePrice, BigDecimal discountPrice) {
        return productRepository.findBySlug(slug).orElseGet(() -> {
            Product product = new Product();
            product.setCategory(category);
            product.setNameI18n(Map.of("en", nameEn, "bn", nameBn));
            product.setDescriptionI18n(Map.of("en", "Description of " + nameEn, "bn", "বর্ণনা"));
            product.setSlug(slug);
            product.setBasePrice(basePrice);
            product.setDiscountPrice(discountPrice);
            product.setSizeType(SizeType.LETTER);
            product.setActive(true);
            product.setFeatured(true);
            return productRepository.save(product);
        });
    }

    protected ProductVariant ensureVariant(Product product, String size, String sku, int stock, int reserved) {
        return variantRepository.findBySku(sku).orElseGet(() -> {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSizeLabel(size);
            variant.setSku(sku);
            variant.setStockQuantity(stock);
            variant.setReservedQuantity(reserved);
            variant.setLowStockThreshold(1);
            variant.setActive(true);
            return variantRepository.save(variant);
        });
    }

    protected MediaAsset ensureMedia(String key) {
        return mediaAssetRepository.findAll().stream()
                .filter(asset -> key.equals(asset.getStorageKey()))
                .findFirst()
                .orElseGet(() -> mediaAssetRepository.save(MediaAsset.builder()
                        .storageKey(key).originalFilename(key).mimeType("image/png")
                        .byteSize(10).width(10).height(10).checksumSha256("checksum").build()));
    }

    protected void attachImage(Product product, MediaAsset media) {
        if (productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).isEmpty()) {
            productImageRepository.save(ProductImage.builder().product(product).mediaAsset(media)
                    .sortOrder(0).primary(true).build());
        }
    }

    protected DeliveryArea ensureDeliveryArea(String name, BigDecimal charge, BigDecimal threshold) {
        return deliveryAreaRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(area -> name.equals(area.getName()))
                .findFirst()
                .orElseGet(() -> {
                    DeliveryArea area = new DeliveryArea();
                    area.setName(name);
                    area.setDistrict("Dhaka");
                    area.setDeliveryCharge(charge);
                    area.setFreeShippingThreshold(threshold);
                    area.setSlaDays(3);
                    area.setActive(true);
                    area.setDisplayOrder(0);
                    return deliveryAreaRepository.save(area);
                });
    }
}
