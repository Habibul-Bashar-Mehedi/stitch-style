package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.CategoryReorderRequest;
import com.stitchstyle.app.dto.request.CategoryRequest;
import com.stitchstyle.app.dto.response.CategoryResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.SizeType;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    /** Hidden container that keeps sold products alive after their category is deleted. */
    private static final String ARCHIVE_SLUG = "archived-products";

    private final CategoryRepository repository;
    private final ProductRepository productRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaService mediaService;
    private final ProductService productService;
    private final CacheEvictionService cacheEvictionService;
    private final int maxDepth;

    public CategoryService(CategoryRepository repository, ProductRepository productRepository,
                           MediaAssetRepository mediaAssetRepository, MediaService mediaService,
                           ProductService productService,
                           CacheEvictionService cacheEvictionService,
                           @Value("${app.catalog.max-depth:4}") int maxDepth) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaService = mediaService;
        this.productService = productService;
        this.cacheEvictionService = cacheEvictionService;
        this.maxDepth = maxDepth;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATEGORY_TREE, key = "#includeHidden ? 'all' : 'visible'")
    public List<CategoryResponse> tree(boolean includeHidden) {
        List<Category> categories = includeHidden
                ? repository.findAllByOrderByDepthAscDisplayOrderAscIdAsc()
                : repository.findByActiveTrueOrderByDepthAscDisplayOrderAsc();
        Map<Long, List<Category>> children = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            if (category.getParent() == null) {
                roots.add(category);
            } else {
                children.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>()).add(category);
            }
        }
        roots.sort(Comparator.comparingInt(Category::getDisplayOrder).thenComparing(Category::getId));
        return roots.stream().map(root -> build(root, children)).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        Category category = repository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));
        if (!category.isActive()) throw new ResourceNotFoundException("Category not found: " + slug);
        return build(category, childrenMap(repository.findAllByOrderByDepthAscDisplayOrderAscIdAsc()));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> homeCards() {
        return repository.findByParentIsNullAndActiveTrueAndShowOnHomeTrueOrderByDisplayOrderAsc()
                .stream().map(c -> build(c, Map.of())).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> navbar() {
        return repository.findByParentIsNullAndActiveTrueAndShowInNavbarTrueOrderByDisplayOrderAsc()
                .stream().map(c -> build(c, Map.of())).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(uniqueSlug(request.slug(), request.name(), null));
        category.setDescription(request.description());
        category.setSizeType(request.sizeType() == null ? SizeType.LETTER : request.sizeType());
        category.setActive(request.active() == null || request.active());
        category.setShowOnHome(Boolean.TRUE.equals(request.showOnHome()));
        category.setShowInNavbar(Boolean.TRUE.equals(request.showInNavbar()));
        category.setImageMedia(resolveMedia(request.imageMediaId()));
        applyParent(category, request.parentId());
        category.setDepth(category.getParent() == null ? 0 : category.getParent().getDepth() + 1);
        category.setDisplayOrder(request.displayOrder() == null ? nextDisplayOrder(category.getParent()) : request.displayOrder());
        repository.save(category);
        cacheEvictionService.evictAfterCommit(CacheNames.CATEGORY_TREE, CacheNames.HOME_CARDS, CacheNames.PRODUCT_LIST);
        return build(category, Map.of());
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getRequired(id);
        if (request.parentId() != null && Objects.equals(request.parentId(), id)) {
            throw new DuplicateResourceException("A category cannot be its own parent.");
        }
        if (request.parentId() != null && isDescendant(id, request.parentId())) {
            throw new DuplicateResourceException("A category cannot be moved under its own descendant.");
        }
        category.setName(request.name());
        category.setSlug(uniqueSlug(request.slug(), request.name(), id));
        category.setDescription(request.description());
        category.setSizeType(request.sizeType() == null ? category.getSizeType() : request.sizeType());
        if (request.active() != null) category.setActive(request.active());
        if (request.showOnHome() != null) category.setShowOnHome(request.showOnHome());
        if (request.showInNavbar() != null) category.setShowInNavbar(request.showInNavbar());
        category.setImageMedia(resolveMedia(request.imageMediaId()));
        applyParent(category, request.parentId());
        category.setDepth(category.getParent() == null ? 0 : category.getParent().getDepth() + 1);
        if (request.displayOrder() != null) category.setDisplayOrder(request.displayOrder());
        repository.save(category);
        recomputeDescendantDepths(category);
        cacheEvictionService.evictAfterCommit(CacheNames.CATEGORY_TREE, CacheNames.HOME_CARDS, CacheNames.PRODUCT_LIST);
        return build(category, childrenMap(repository.findAllByOrderByDepthAscDisplayOrderAscIdAsc()));
    }

    @Transactional
    public void setVisibility(Long id, boolean active) {
        Category category = getRequired(id);
        category.setActive(active);
        repository.save(category);
        cacheEvictionService.evictAfterCommit(CacheNames.CATEGORY_TREE, CacheNames.HOME_CARDS, CacheNames.PRODUCT_LIST);
    }

    @Transactional
    public void reorder(CategoryReorderRequest request) {
        List<Category> siblings = request.parentId() == null
                ? repository.findByParentIsNullOrderByDisplayOrderAsc()
                : repository.findByParentIdOrderByDisplayOrderAsc(request.parentId());
        Map<Long, Category> byId = siblings.stream().collect(Collectors.toMap(Category::getId, c -> c));
        if (byId.size() != request.orderedIds().size() || !byId.keySet().containsAll(request.orderedIds())) {
            throw new DuplicateResourceException("Reorder list must contain exactly the siblings of the parent.");
        }
        for (int i = 0; i < request.orderedIds().size(); i++) {
            byId.get(request.orderedIds().get(i)).setDisplayOrder(i);
        }
        repository.saveAll(siblings);
        cacheEvictionService.evictAfterCommit(CacheNames.CATEGORY_TREE, CacheNames.HOME_CARDS, CacheNames.PRODUCT_LIST);
    }

    @Transactional
    public void delete(Long id) {
        Category category = getRequired(id);
        if (ARCHIVE_SLUG.equals(category.getSlug())) {
            throw new DuplicateResourceException("The archived-products container cannot be deleted.");
        }
        if (repository.countByParentId(id) > 0) {
            throw new DuplicateResourceException("Category has child categories; delete or move them first.");
        }
        List<Product> products = productRepository.findByCategoryId(id);
        long active = products.stream().filter(Product::isActive).count();
        if (active > 0) {
            throw new DuplicateResourceException("Category has " + active
                    + " active product(s); move them to another category or delete them first.");
        }
        Category archive = products.isEmpty() ? null : archiveCategory();
        for (Product product : products) {
            if (!productService.deleteOrDeactivate(product)) {
                // Sold products must survive for invoices and returns.
                product.setCategory(archive);
                productRepository.save(product);
            }
        }
        repository.delete(category);
        cacheEvictionService.evictAfterCommit(CacheNames.CATEGORY_TREE, CacheNames.HOME_CARDS, CacheNames.PRODUCT_LIST);
    }

    private Category archiveCategory() {
        return repository.findBySlug(ARCHIVE_SLUG).orElseGet(() -> {
            Category archive = new Category();
            archive.setName("Archived products");
            archive.setSlug(ARCHIVE_SLUG);
            archive.setDescription("Kept only so deleted collections do not break order history.");
            archive.setSizeType(SizeType.LETTER);
            archive.setActive(false);
            archive.setShowOnHome(false);
            archive.setShowInNavbar(false);
            archive.setDepth(0);
            archive.setDisplayOrder(Integer.MAX_VALUE);
            return repository.save(archive);
        });
    }

    public Category getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private CategoryResponse build(Category category, Map<Long, List<Category>> children) {
        List<CategoryResponse> childResponses = children.getOrDefault(category.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(Category::getDisplayOrder).thenComparing(Category::getId))
                .map(child -> build(child, children))
                .toList();
        return new CategoryResponse(category.getId(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getName(), category.getSlug(), category.getDescription(),
                mediaService.url(category.getImageMedia()),
                category.getImageMedia() == null ? null : category.getImageMedia().getId(),
                category.getSizeType(), category.getDisplayOrder(),
                category.getDepth(), category.isActive(), category.isShowOnHome(), category.isShowInNavbar(),
                childResponses);
    }

    private Map<Long, List<Category>> childrenMap(List<Category> categories) {
        Map<Long, List<Category>> children = new HashMap<>();
        for (Category category : categories) {
            if (category.getParent() != null) {
                children.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>()).add(category);
            }
        }
        return children;
    }

    private void applyParent(Category category, Long parentId) {
        if (parentId == null) {
            category.setParent(null);
            return;
        }
        Category parent = getRequired(parentId);
        if (parent.getDepth() + 1 > maxDepth) {
            throw new DuplicateResourceException("Maximum category depth is " + maxDepth + ".");
        }
        category.setParent(parent);
    }

    private boolean isDescendant(Long categoryId, Long candidateParentId) {
        Category current = getRequired(candidateParentId);
        while (current != null) {
            if (Objects.equals(current.getId(), categoryId)) return true;
            current = current.getParent();
        }
        return false;
    }

    private void recomputeDescendantDepths(Category parent) {
        List<Category> children = repository.findByParentIdOrderByDisplayOrderAsc(parent.getId());
        for (Category child : children) {
            child.setDepth(parent.getDepth() + 1);
            repository.save(child);
            recomputeDescendantDepths(child);
        }
    }

    private int nextDisplayOrder(Category parent) {
        List<Category> siblings = parent == null
                ? repository.findByParentIsNullOrderByDisplayOrderAsc()
                : repository.findByParentIdOrderByDisplayOrderAsc(parent.getId());
        return siblings.stream().mapToInt(Category::getDisplayOrder).max().orElse(-1) + 1;
    }

    private String uniqueSlug(String requested, String name, Long excludeId) {
        String base = (requested == null || requested.isBlank()) ? slugify(name) : slugify(requested);
        String candidate = base;
        int suffix = 2;
        while (excludeId == null ? repository.existsBySlug(candidate) : repository.existsBySlugAndIdNot(candidate, excludeId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String slug = value == null ? "category" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "category" : slug;
    }

    private MediaAsset resolveMedia(Long mediaId) {
        return mediaId == null ? null : mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + mediaId));
    }
}
