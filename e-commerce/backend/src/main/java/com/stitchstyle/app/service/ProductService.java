package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.ProductRequest;
import com.stitchstyle.app.dto.request.VariantRequest;
import com.stitchstyle.app.dto.response.PageResponse;
import com.stitchstyle.app.dto.response.ProductImageResponse;
import com.stitchstyle.app.dto.response.ProductResponse;
import com.stitchstyle.app.dto.response.VariantResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final StockReservationRepository reservationRepository;
    private final MediaService mediaService;
    private final CacheEvictionService cacheEvictionService;
    private final int latestLimit;

    public ProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                          ProductImageRepository imageRepository, MediaAssetRepository mediaAssetRepository,
                          CategoryRepository categoryRepository, InventoryMovementRepository movementRepository,
                          OrderItemRepository orderItemRepository, CartItemRepository cartItemRepository,
                          StockReservationRepository reservationRepository,
                          MediaService mediaService, CacheEvictionService cacheEvictionService,
                          @Value("${app.catalog.home.latest-products:8}") int latestLimit) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.categoryRepository = categoryRepository;
        this.movementRepository = movementRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.reservationRepository = reservationRepository;
        this.mediaService = mediaService;
        this.cacheEvictionService = cacheEvictionService;
        this.latestLimit = latestLimit;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCT_DETAIL, key = "#slug + '|' + #lang")
    public ProductResponse getBySlug(String slug, String lang) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        if (!product.isActive()) throw new ResourceNotFoundException("Product not found: " + slug);
        return toResponse(product, lang);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id, String lang) {
        return toResponse(getRequired(id), lang);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PRODUCT_LIST, key = "T(com.stitchstyle.app.service.ProductService).listKey(#categorySlug, #q, #minPrice, #maxPrice, #sizeLabel, #page, #size, #sort, #lang)")
    public PageResponse<ProductResponse> search(String categorySlug, String q, BigDecimal minPrice, BigDecimal maxPrice,
                                                String sizeLabel, int page, int size, String sort, String lang) {
        List<Long> categoryIds = null;
        if (categorySlug != null && !categorySlug.isBlank()) {
            Category category = categoryRepository.findBySlug(categorySlug).orElse(null);
            if (category == null) {
                return new PageResponse<>(List.of(), Math.max(page, 0), Math.min(Math.max(size, 1), 100), 0, 0);
            }
            categoryIds = new ArrayList<>();
            collectCategoryIds(category, categoryIds);
        }
        Specification<Product> spec = buildSpecification(categoryIds, q, minPrice, maxPrice, sizeLabel);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sortFor(sort));
        Page<Product> result = productRepository.findAll(spec, pageable);
        List<ProductResponse> content = result.getContent().stream().map(p -> toResponse(p, lang)).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> latest(String lang) {
        return productRepository.findTop12ByActiveTrueOrderByCreatedAtDesc().stream()
                .limit(latestLimit)
                .map(p -> toResponse(p, lang))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> related(String slug, String lang) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return productRepository.findAll().stream()
                .filter(p -> p.isActive() && !p.getId().equals(product.getId())
                        && p.getCategory().getId().equals(product.getCategory().getId()))
                .limit(4)
                .map(p -> toResponse(p, lang))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> activeSlugs() {
        return productRepository.findAll().stream().filter(Product::isActive).map(Product::getSlug).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> adminList(int page, int size, String lang) {
        Page<Product> result = productRepository.findAll(PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.getContent().stream().map(p -> toResponse(p, lang)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ProductResponse create(ProductRequest request, String lang) {
        Product product = new Product();
        apply(product, request);
        productRepository.save(product);
        syncVariants(product, request.variants());
        syncImages(product, request.imageMediaIds());
        evict(product);
        return toResponse(product, lang);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, String lang) {
        Product product = getRequired(id);
        apply(product, request);
        productRepository.save(product);
        syncVariants(product, request.variants());
        syncImages(product, request.imageMediaIds());
        evict(product);
        return toResponse(product, lang);
    }

    /**
     * Delete a product outright when it was never sold. Products that already
     * appear in an order are only deactivated, so order and return history keeps
     * pointing at a row that exists.
     *
     * @return {@code true} when the row was removed, {@code false} when it was
     *         deactivated instead
     */
    @Transactional
    public boolean delete(Long id) {
        return deleteOrDeactivate(getRequired(id));
    }

    @Transactional
    public boolean deleteOrDeactivate(Product product) {
        if (orderItemRepository.countByProductId(product.getId()) > 0) {
            if (product.isActive()) {
                product.setActive(false);
                productRepository.save(product);
            }
            evict(product);
            return false;
        }
        List<ProductVariant> variants = variantRepository.findByProductIdOrderBySizeLabelAsc(product.getId());
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        if (!variantIds.isEmpty()) {
            cartItemRepository.deleteByVariantIdIn(variantIds);
            reservationRepository.deleteByVariantIdIn(variantIds);
            movementRepository.deleteByVariantIdIn(variantIds);
            cartItemRepository.flush();
            reservationRepository.flush();
            movementRepository.flush();
            variantRepository.deleteAll(variants);
            variantRepository.flush();
        }
        productRepository.delete(product);
        productRepository.flush();
        evict(product);
        return true;
    }

    @Transactional
    public VariantResponse adjustStock(Long productId, Long variantId, int delta, User actor) {
        getRequired(productId);
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Variant does not belong to the product.");
        }
        int newStock = variant.getStockQuantity() + delta;
        if (newStock < variant.getReservedQuantity()) {
            throw new DuplicateResourceException("Stock cannot fall below the reserved quantity.");
        }
        variant.setStockQuantity(newStock);
        variantRepository.save(variant);
        movementRepository.save(InventoryMovement.builder()
                .variant(variant)
                .delta(delta)
                .reason(InventoryReason.MANUAL_ADJUST)
                .referenceType("ADMIN")
                .referenceId(actor == null ? null : actor.getId())
                .actorUser(actor)
                .balanceAfter(newStock)
                .build());
        evict(variant.getProduct());
        return toVariant(variant);
    }

    public Product getRequired(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private void apply(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
        product.setCategory(category);
        Map<String, String> names = request.nameI18n() == null || request.nameI18n().isEmpty()
                ? Map.of("en", "Untitled") : request.nameI18n();
        product.setNameI18n(new HashMap<>(names));
        product.setDescriptionI18n(request.descriptionI18n() == null ? new HashMap<>() : new HashMap<>(request.descriptionI18n()));
        product.setSlug(uniqueSlug(request.slug(), names, product.getId()));
        product.setBasePrice(request.basePrice());
        product.setDiscountPrice(request.discountPrice());
        product.setSizeType(request.sizeType() == null ? SizeType.LETTER : request.sizeType());
        product.setOriginCountry(request.originCountry());
        product.setBrand(request.brand());
        product.setModel(request.model());
        product.setDeliveryEstimate(request.deliveryEstimate());
        product.setActive(request.active() == null || request.active());
        product.setFeatured(Boolean.TRUE.equals(request.featured()));
    }

    private void syncVariants(Product product, List<VariantRequest> requests) {
        List<VariantRequest> safe = requests == null ? List.of() : requests;
        Set<Long> kept = new HashSet<>();
        for (VariantRequest request : safe) {
            ProductVariant variant = request.id() == null ? new ProductVariant()
                    : variantRepository.findById(request.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + request.id()));
            if (variant.getProduct() != null && !variant.getProduct().getId().equals(product.getId())) {
                throw new DuplicateResourceException("Variant does not belong to this product.");
            }
            if (variant.getSku() == null || !variant.getSku().equals(request.sku())) {
                variantRepository.findBySku(request.sku()).ifPresent(existing -> {
                    if (!existing.getId().equals(variant.getId())) {
                        throw new DuplicateResourceException("SKU already exists: " + request.sku());
                    }
                });
            }
            variant.setProduct(product);
            variant.setSizeLabel(request.sizeLabel());
            variant.setSku(request.sku());
            variant.setStockQuantity(request.stockQuantity());
            variant.setLowStockThreshold(request.lowStockThreshold() == null ? 5 : request.lowStockThreshold());
            variant.setPriceOverride(request.priceOverride());
            variant.setActive(request.active() == null || request.active());
            variantRepository.save(variant);
            kept.add(variant.getId());
        }
        if (product.getId() != null) {
            for (ProductVariant existing : variantRepository.findByProductIdOrderBySizeLabelAsc(product.getId())) {
                if (!kept.contains(existing.getId())) {
                    existing.setActive(false);
                    variantRepository.save(existing);
                }
            }
        }
    }

    private void syncImages(Product product, List<Long> mediaIds) {
        imageRepository.deleteByProductId(product.getId());
        if (mediaIds == null) return;
        int order = 0;
        for (Long mediaId : mediaIds) {
            MediaAsset media = mediaAssetRepository.findById(mediaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + mediaId));
            imageRepository.save(ProductImage.builder()
                    .product(product)
                    .mediaAsset(media)
                    .sortOrder(order)
                    .primary(order == 0)
                    .build());
            order++;
        }
    }

    private void evict(Product product) {
        cacheEvictionService.evictAfterCommit(CacheNames.PRODUCT_LIST, CacheNames.PRODUCT_DETAIL,
                CacheNames.PRODUCT_STOCK, CacheNames.HOME_CARDS);
    }

    private ProductResponse toResponse(Product product, String lang) {
        List<VariantResponse> variants = variantRepository.findByProductIdOrderBySizeLabelAsc(product.getId())
                .stream().map(this::toVariant).toList();
        List<ProductImageResponse> images = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .stream().map(image -> new ProductImageResponse(image.getId(), image.getMediaAsset().getId(),
                        mediaService.url(image.getMediaAsset()), image.getSortOrder(), image.isPrimary())).toList();
        return new ProductResponse(product.getId(), product.getCategory().getId(), product.getCategory().getSlug(),
                product.getNameI18n(), product.getSlug(), product.getDescriptionI18n(),
                resolve(product.getNameI18n(), lang), resolve(product.getDescriptionI18n(), lang),
                product.getBasePrice(), product.getDiscountPrice(), effectivePrice(product), product.getSizeType(),
                product.getOriginCountry(), product.getBrand(), product.getModel(), product.getDeliveryEstimate(),
                product.isActive(), product.isFeatured(), variants, images, product.getCreatedAt());
    }

    private VariantResponse toVariant(ProductVariant variant) {
        return new VariantResponse(variant.getId(), variant.getSizeLabel(), variant.getSku(), variant.getStockQuantity(),
                variant.getLowStockThreshold(), variant.getReservedQuantity(), variant.available(),
                variant.getPriceOverride(), variant.isActive());
    }

    private BigDecimal effectivePrice(Product product) {
        if (product.getDiscountPrice() != null) return product.getDiscountPrice();
        return product.getBasePrice();
    }

    private String resolve(Map<String, String> values, String lang) {
        if (values == null) return "";
        String key = lang == null ? "en" : lang;
        return values.getOrDefault(key, values.getOrDefault("en", values.values().stream().findFirst().orElse("")));
    }

    private String uniqueSlug(String requested, Map<String, String> names, Long excludeId) {
        String base = (requested == null || requested.isBlank())
                ? slugify(names.getOrDefault("en", names.values().stream().findFirst().orElse("product")))
                : slugify(requested);
        String candidate = base;
        int suffix = 2;
        while (excludeId == null ? productRepository.existsBySlug(candidate) : productRepository.existsBySlugAndIdNot(candidate, excludeId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String slug = value == null ? "product" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "product" : slug;
    }

    private Sort sortFor(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "basePrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "basePrice");
            case "name_asc" -> Sort.by(Sort.Direction.ASC, "slug");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private void collectCategoryIds(Category category, List<Long> ids) {
        ids.add(category.getId());
        for (Category child : categoryRepository.findByParentIdOrderByDisplayOrderAsc(category.getId())) {
            collectCategoryIds(child, ids);
        }
    }

    private Specification<Product> buildSpecification(List<Long> categoryIds, String q, BigDecimal minPrice,
                                                      BigDecimal maxPrice, String sizeLabel) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (categoryIds != null && !categoryIds.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
                var name = cb.function("jsonb_extract_path_text", String.class, root.get("nameI18n"), cb.literal("en"));
                var description = cb.function("jsonb_extract_path_text", String.class, root.get("descriptionI18n"), cb.literal("en"));
                predicates.add(cb.or(cb.like(cb.lower(name), pattern), cb.like(cb.lower(description), pattern)));
            }
            var price = cb.<BigDecimal>coalesce(root.get("discountPrice"), root.get("basePrice"));
            if (minPrice != null) predicates.add(cb.greaterThanOrEqualTo(price, minPrice));
            if (maxPrice != null) predicates.add(cb.lessThanOrEqualTo(price, maxPrice));
            if (sizeLabel != null && !sizeLabel.isBlank()) {
                var variants = root.join("variants", JoinType.INNER);
                predicates.add(cb.equal(variants.get("sizeLabel"), sizeLabel));
                predicates.add(cb.isTrue(variants.get("active")));
                query.distinct(true);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static String listKey(String categorySlug, String q, BigDecimal minPrice, BigDecimal maxPrice,
                                 String sizeLabel, int page, int size, String sort, String lang) {
        return (categorySlug == null ? "" : categorySlug) + "|" + (q == null ? "" : q) + "|"
                + (minPrice == null ? "" : minPrice.stripTrailingZeros().toPlainString()) + "|"
                + (maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString()) + "|"
                + (sizeLabel == null ? "" : sizeLabel) + "|" + page + "|" + size + "|"
                + (sort == null ? "newest" : sort) + "|" + (lang == null ? "en" : lang);
    }
}
