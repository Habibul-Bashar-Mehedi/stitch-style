package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.dto.request.CategoryRequest;
import com.stitchstyle.app.dto.request.CategoryReorderRequest;
import com.stitchstyle.app.dto.response.CategoryResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.SizeType;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private CategoryRepository repository;
    private ProductRepository productRepository;
    private MediaAssetRepository mediaAssetRepository;
    private ProductService productService;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        mediaAssetRepository = mock(MediaAssetRepository.class);
        productService = mock(ProductService.class);
        service = new CategoryService(repository, productRepository, mediaAssetRepository,
                mock(MediaService.class), productService, mock(CacheEvictionService.class), 4);
    }

    @Test
    void treeNestsChildrenAndSortsSiblings() {
        Category men = category(1L, "Men", null, 0);
        Category women = category(2L, "Women", null, 1);
        Category shirts = category(3L, "Shirts", men, 1);
        Category pants = category(4L, "Pants", men, 0);
        when(repository.findByActiveTrueOrderByDepthAscDisplayOrderAsc())
                .thenReturn(List.of(men, women, shirts, pants));

        List<CategoryResponse> tree = service.tree(false);

        assertThat(tree).extracting(CategoryResponse::name).containsExactly("Men", "Women");
        assertThat(tree.get(0).children()).extracting(CategoryResponse::name)
                .containsExactly("Pants", "Shirts");
    }

    @Test
    void getBySlugRejectsInactiveCategory() {
        Category hidden = category(1L, "Hidden", null, 0);
        hidden.setActive(false);
        when(repository.findBySlug("hidden")).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.getBySlug("hidden")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getBySlugBuildsNestedResponse() {
        Category men = category(1L, "Men", null, 0);
        Category shirts = category(2L, "Shirts", men, 0);
        when(repository.findBySlug("men")).thenReturn(Optional.of(men));
        when(repository.findAllByOrderByDepthAscDisplayOrderAscIdAsc()).thenReturn(List.of(men, shirts));

        CategoryResponse response = service.getBySlug("men");

        assertThat(response.children()).hasSize(1);
        assertThat(response.children().get(0).depth()).isEqualTo(1);
    }

    @Test
    void createSlugifiesAndAppliesDefaults() {
        when(repository.existsBySlug("men-s-panjabi")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(9L);
            return category;
        });
        when(repository.findByParentIsNullOrderByDisplayOrderAsc()).thenReturn(List.of());

        CategoryResponse response = service.create(new CategoryRequest("Men's Panjabi!", null, "d", null,
                null, null, null, null, null, null));

        assertThat(response.slug()).isEqualTo("men-s-panjabi");
        assertThat(response.sizeType()).isEqualTo(SizeType.LETTER);
        assertThat(response.displayOrder()).isZero();
        assertThat(response.active()).isTrue();
    }

    @Test
    void createAppendsSuffixWhenSlugExists() {
        when(repository.existsBySlug("sale")).thenReturn(true);
        when(repository.existsBySlug("sale-2")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(10L);
            return category;
        });
        when(repository.findByParentIsNullOrderByDisplayOrderAsc()).thenReturn(List.of());

        CategoryResponse response = service.create(new CategoryRequest("Sale", "sale", null, null, null,
                SizeType.LETTER, null, true, false, false));

        assertThat(response.slug()).isEqualTo("sale-2");
    }

    @Test
    void updateRejectsSelfParentAndDescendantCycle() {
        Category root = category(1L, "Root", null, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(root));

        assertThatThrownBy(() -> service.update(1L, requestWithParent("Root", 1L)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("own parent");

        Category parent = category(2L, "Parent", root, 1);
        Category child = category(3L, "Child", parent, 2);
        when(repository.findById(2L)).thenReturn(Optional.of(parent));
        when(repository.findById(3L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.update(2L, requestWithParent("Parent", 3L)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("descendant");
    }

    @Test
    void updateRecomputesDepthAndEvicts() {
        Category root = category(1L, "Root", null, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(root));
        when(repository.existsBySlugAndIdNot("root", 1L)).thenReturn(false);
        when(repository.findAllByOrderByDepthAscDisplayOrderAscIdAsc()).thenReturn(List.of(root));

        CategoryResponse response = service.update(1L, requestWithParent("Root", null));

        assertThat(response.depth()).isZero();
        verify(repository).save(root);
    }

    @Test
    void maximumDepthIsEnforced() {
        Category parent = category(1L, "Level", null, 4);
        parent.setDepth(4);
        when(repository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.create(new CategoryRequest("Too deep", null, null, 1L, null,
                SizeType.LETTER, null, true, false, false)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Maximum category depth");
    }

    @Test
    void deleteRejectsArchiveContainerChildrenAndActiveProducts() {
        Category archive = category(1L, "Archived", null, 0);
        archive.setSlug("archived-products");
        when(repository.findById(1L)).thenReturn(Optional.of(archive));
        assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("archived-products");

        Category parent = category(2L, "Parent", null, 0);
        when(repository.findById(2L)).thenReturn(Optional.of(parent));
        when(repository.countByParentId(2L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(2L)).hasMessageContaining("child categories");

        Category leaf = category(3L, "Leaf", null, 0);
        Product active = Product.builder().id(9L).nameI18n(java.util.Map.of("en", "P")).slug("p").active(true).build();
        when(repository.findById(3L)).thenReturn(Optional.of(leaf));
        when(productRepository.findByCategoryId(3L)).thenReturn(List.of(active));
        assertThatThrownBy(() -> service.delete(3L)).hasMessageContaining("active product");
    }

    @Test
    void deletingAnEmptyCategoryRemovesIt() {
        Category leaf = category(4L, "Leaf", null, 0);
        when(repository.findById(4L)).thenReturn(Optional.of(leaf));
        when(productRepository.findByCategoryId(4L)).thenReturn(List.of());

        service.delete(4L);

        verify(repository).delete(leaf);
        verify(productService, never()).deleteOrDeactivate(any());
    }

    @Test
    void reorderValidatesSiblingSetAndPersistsOrder() {
        Category a = category(1L, "A", null, 0);
        Category b = category(2L, "B", null, 1);
        when(repository.findByParentIsNullOrderByDisplayOrderAsc()).thenReturn(List.of(a, b));

        service.reorder(new CategoryReorderRequest(List.of(2L, 1L), null));

        assertThat(b.getDisplayOrder()).isZero();
        assertThat(a.getDisplayOrder()).isEqualTo(1);
        verify(repository).saveAll(List.of(a, b));

        assertThatThrownBy(() -> service.reorder(new CategoryReorderRequest(List.of(99L), null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void setVisibilityUpdatesFlag() {
        Category category = category(1L, "A", null, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(category));

        service.setVisibility(1L, false);

        assertThat(category.isActive()).isFalse();
        verify(repository).save(category);
    }

    @Test
    void homeCardsAndNavbarUseDedicatedQueries() {
        when(repository.findByParentIsNullAndActiveTrueAndShowOnHomeTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category(1L, "Home", null, 0)));
        when(repository.findByParentIsNullAndActiveTrueAndShowInNavbarTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category(2L, "Nav", null, 0)));

        assertThat(service.homeCards()).extracting(CategoryResponse::name).containsExactly("Home");
        assertThat(service.navbar()).extracting(CategoryResponse::name).containsExactly("Nav");
    }

    private static CategoryRequest requestWithParent(String name, Long parentId) {
        return new CategoryRequest(name, name.toLowerCase(), null, parentId, null,
                SizeType.LETTER, 0, true, false, false);
    }

    private static Category category(Long id, String name, Category parent, int displayOrder) {
        return Category.builder().id(id).name(name).slug(name.toLowerCase().replace(' ', '-'))
                .parent(parent).displayOrder(displayOrder)
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .sizeType(SizeType.LETTER).active(true).build();
    }
}
