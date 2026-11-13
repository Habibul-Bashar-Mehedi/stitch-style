package com.stitchstyle.app.service;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.dto.response.ProductResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.SizeType;
import com.stitchstyle.app.repository.CartItemRepository;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.InventoryMovementRepository;
import com.stitchstyle.app.repository.MediaAssetRepository;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.ProductImageRepository;
import com.stitchstyle.app.repository.ProductRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private ProductImageRepository imageRepository;
    private OrderItemRepository orderItemRepository;
    private CartItemRepository cartItemRepository;
    private StockReservationRepository reservationRepository;
    private InventoryMovementRepository movementRepository;
    private CacheEvictionService cacheEvictionService;
    private ProductService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        imageRepository = mock(ProductImageRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        cacheEvictionService = mock(CacheEvictionService.class);
        service = new ProductService(productRepository, variantRepository, imageRepository,
                mock(MediaAssetRepository.class), mock(CategoryRepository.class), movementRepository,
                orderItemRepository, cartItemRepository, reservationRepository, mock(MediaService.class),
                cacheEvictionService, 8);
    }

    @Test
    void unknownCategorySlugReturnsAnEmptyPage() {
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        service = new ProductService(productRepository, variantRepository, imageRepository,
                mock(MediaAssetRepository.class), categoryRepository, movementRepository,
                orderItemRepository, cartItemRepository, reservationRepository, mock(MediaService.class),
                cacheEvictionService, 8);
        when(categoryRepository.findBySlug("nope")).thenReturn(Optional.empty());

        var response = service.search("nope", null, null, null, null, 0, 20, "newest", "en");

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchClampsPagingAndMapsContent() {
        Product product = product(1L, "panjabi", true);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(variantRepository.findByProductIdOrderBySizeLabelAsc(1L)).thenReturn(List.of());

        var response = service.search(null, null, null, null, null, -2, 1000, "price_desc", "bn");

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.content().get(0).name()).isEqualTo("পাঞ্জাবি");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void latestRespectsConfiguredLimitAndMaps() {
        when(productRepository.findTop12ByActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(product(1L, "a", true), product(2L, "b", true)));

        assertThat(service.latest("en")).hasSize(2);
    }

    @Test
    void relatedExcludesTheCurrentProductAndInactiveOnesButLimitsToFour() {
        Category category = Category.builder().id(5L).name("C").slug("c").build();
        Product current = product(1L, "current", true);
        current.setCategory(category);
        when(productRepository.findBySlug("current")).thenReturn(Optional.of(current));
        Product sameCategory = product(2L, "same", true);
        sameCategory.setCategory(category);
        Product inactive = product(3L, "off", false);
        inactive.setCategory(category);
        when(productRepository.findAll()).thenReturn(List.of(current, sameCategory, inactive));

        assertThat(service.related("current", "en")).extracting(ProductResponse::slug)
                .containsExactly("same");
    }

    @Test
    void relatedFailsForUnknownProduct() {
        when(productRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.related("missing", "en")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activeSlugsOnlyReturnsActiveProducts() {
        when(productRepository.findAll()).thenReturn(List.of(
                product(1L, "on", true), product(2L, "off", false)));

        assertThat(service.activeSlugs()).containsExactly("on");
    }

    @Test
    void getBySlugRejectsInactiveProducts() {
        Product inactive = product(1L, "off", false);
        when(productRepository.findBySlug("off")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getBySlug("off", "en")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminListClampsSize() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(Page.<Product>empty());
        service.adminList(0, 500, "en");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void deleteArchivesProductsThatWereSold() {
        Product product = product(1L, "sold", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.countByProductId(1L)).thenReturn(2L);

        boolean deleted = service.delete(1L);

        assertThat(deleted).isFalse();
        assertThat(product.isActive()).isFalse();
        verify(productRepository).save(product);
        verify(productRepository, never()).delete(product);
        verify(cacheEvictionService).evictAfterCommit(any(), any(), any(), any());
    }

    @Test
    void deleteRemovesNeverSoldProductAndItsDependencies() {
        Product product = product(1L, "new", true);
        ProductVariant variant = ProductVariant.builder().id(10L).product(product).sizeLabel("M")
                .sku("SKU-1").stockQuantity(1).active(true).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.countByProductId(1L)).thenReturn(0L);
        when(variantRepository.findByProductIdOrderBySizeLabelAsc(1L)).thenReturn(List.of(variant));

        assertThat(service.delete(1L)).isTrue();

        verify(cartItemRepository).deleteByVariantIdIn(List.of(10L));
        verify(reservationRepository).deleteByVariantIdIn(List.of(10L));
        verify(movementRepository).deleteByVariantIdIn(List.of(10L));
        verify(variantRepository).deleteAll(List.of(variant));
        verify(productRepository).delete(product);
    }

    @Test
    void stockAdjustmentCannotDropBelowReservedQuantity() {
        Product product = product(1L, "p", true);
        ProductVariant variant = ProductVariant.builder().id(10L).product(product).sizeLabel("M")
                .sku("SKU-1").stockQuantity(10).reservedQuantity(5).active(true).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> service.adjustStock(1L, 10L, -6, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("reserved");

        var response = service.adjustStock(1L, 10L, -4, null);
        assertThat(response.stockQuantity()).isEqualTo(6);
        verify(movementRepository).save(any());
        verify(cacheEvictionService).evictAfterCommit(any(), any(), any(), any());
    }

    @Test
    void stockAdjustmentValidatesVariantOwnership() {
        Product product = product(1L, "p", true);
        Product other = product(2L, "other", true);
        ProductVariant variant = ProductVariant.builder().id(10L).product(other).sizeLabel("M")
                .sku("SKU-1").stockQuantity(10).active(true).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> service.adjustStock(1L, 10L, 1, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void listKeyDistinguishesEveryFilterIncludingNullHandling() {
        String base = ProductService.listKey(null, null, null, null, null, 0, 20, null, null);
        assertThat(base).isEqualTo("|||||0|20|newest|en");
        assertThat(base).isNotEqualTo(ProductService.listKey("men", null, null, null, null, 0, 20, null, null));
    }

    private static Product product(Long id, String slug, boolean active) {
        return Product.builder().id(id).slug(slug)
                .category(Category.builder().id(id + 100).name("Cat").slug("cat-" + id).build())
                .nameI18n(Map.of("en", "Panjabi", "bn", "পাঞ্জাবি"))
                .descriptionI18n(Map.of("en", "Description"))
                .basePrice(new BigDecimal("1000.00")).sizeType(SizeType.LETTER)
                .active(active).createdAt(Instant.now()).build();
    }
}
