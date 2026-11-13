package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CartPriceRequest;
import com.stitchstyle.app.dto.response.CartResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Cart;
import com.stitchstyle.app.model.entity.CartItem;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.CartItemRepository;
import com.stitchstyle.app.repository.CartRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ProductVariantRepository variantRepository;
    private UserRepository userRepository;
    private CartService service;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        userRepository = mock(UserRepository.class);
        service = new CartService(cartRepository, cartItemRepository, variantRepository, userRepository);
    }

    @Test
    void priceUsesDiscountPriceAndReportsStockShortage() {
        ProductVariant variant = variant(10L, "M", 3, 2, new BigDecimal("1200.00"), new BigDecimal("999.00"));
        when(variantRepository.findById(10L)).thenReturn(Optional.of(variant));

        CartResponse response = service.price(new CartPriceRequest(
                List.of(new CartLineRequest(10L, 2, null))), "en");

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).unitPrice()).isEqualByComparingTo("999.00");
        assertThat(response.lines().get(0).lineTotal()).isEqualByComparingTo("1998.00");
        assertThat(response.lines().get(0).selected()).isTrue();
        assertThat(response.lines().get(0).stockChanged()).isTrue();
        assertThat(response.subtotal()).isEqualByComparingTo("1998.00");
        // base 1200 * 2 - discounted 999 * 2
        assertThat(response.discountTotal()).isEqualByComparingTo("402.00");
        assertThat(response.grandTotal()).isEqualByComparingTo("1998.00");
    }

    @Test
    void priceClampsNonPositiveQuantityToOneAndSelectsRequestedFlag() {
        ProductVariant variant = variant(11L, "L", 5, 0, new BigDecimal("500.00"), null);
        when(variantRepository.findById(11L)).thenReturn(Optional.of(variant));

        CartResponse response = service.price(new CartPriceRequest(
                List.of(new CartLineRequest(11L, 0, false))), "bn");

        assertThat(response.lines().get(0).quantity()).isEqualTo(1);
        assertThat(response.lines().get(0).selected()).isFalse();
        assertThat(response.lines().get(0).productName()).isEqualTo("পাঞ্জাবি");
    }

    @Test
    void priceRejectsMissingOrInactiveProducts() {
        when(variantRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.price(new CartPriceRequest(List.of(new CartLineRequest(99L, 1, true))), "en"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Variant not found");

        ProductVariant inactive = variant(12L, "M", 1, 0, BigDecimal.TEN, null);
        inactive.setActive(false);
        when(variantRepository.findById(12L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.price(new CartPriceRequest(List.of(new CartLineRequest(12L, 1, true))), "en"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void emptyCartWhenNoCartExists() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CartResponse response = service.getCart(1L, "en");

        assertThat(response.lines()).isEmpty();
        assertThat(response.subtotal()).isEqualByComparingTo("0.00");
        assertThat(response.grandTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void getCartSkipsUnselectedLinesFromTotalsButKeepsThem() {
        Cart cart = Cart.builder().id(5L).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        ProductVariant selectedVariant = variant(20L, "M", 10, 0, new BigDecimal("750.00"), null);
        ProductVariant unselectedVariant = variant(21L, "L", 10, 0, new BigDecimal("800.00"), null);
        when(cartItemRepository.findByCartIdOrderByIdAsc(5L)).thenReturn(List.of(
                CartItem.builder().cart(cart).variant(selectedVariant).quantity(2).selected(true).build(),
                CartItem.builder().cart(cart).variant(unselectedVariant).quantity(1).selected(false).build()));

        CartResponse response = service.getCart(1L, "en");

        assertThat(response.lines()).hasSize(2);
        assertThat(response.subtotal()).isEqualByComparingTo("1500.00");
        assertThat(response.grandTotal()).isEqualByComparingTo("1500.00");
        assertThat(response.lines().get(1).selected()).isFalse();
    }

    @Test
    void replaceCreatesCartWhenMissingAndCapsQuantityAtAvailableStock() {
        User user = user(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.empty());
        Cart saved = Cart.builder().id(50L).user(user).build();
        when(cartRepository.save(any(Cart.class))).thenReturn(saved);
        ProductVariant variant = variant(30L, "M", 5, 3, new BigDecimal("100.00"), null);
        when(variantRepository.findById(30L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCartIdAndVariantId(50L, 30L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartIdOrderByIdAsc(50L)).thenReturn(List.of());

        service.replace(7L, List.of(new CartLineRequest(30L, 4, true)), "en");

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
        verify(cartItemRepository).deleteByCartId(50L);
    }

    @Test
    void replaceRejectsOutOfStockVariant() {
        User user = user(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Cart saved = Cart.builder().id(51L).user(user).build();
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(saved));
        ProductVariant variant = variant(31L, "M", 1, 1, BigDecimal.TEN, null);
        when(variantRepository.findById(31L)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> service.replace(7L, List.of(new CartLineRequest(31L, 1, true)), "en"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("out of stock");
    }

    @Test
    void mergeKeepsTheLargerQuantityAndAppliesSelectionFlag() {
        User user = user(8L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        Cart cart = Cart.builder().id(60L).user(user).build();
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));
        ProductVariant variant = variant(40L, "M", 10, 0, BigDecimal.TEN, null);
        when(variantRepository.findById(40L)).thenReturn(Optional.of(variant));
        CartItem existing = CartItem.builder().id(1L).cart(cart).variant(variant).quantity(2).selected(true).build();
        when(cartItemRepository.findByCartIdAndVariantId(60L, 40L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findByCartIdOrderByIdAsc(60L)).thenReturn(List.of(existing));

        service.merge(8L, List.of(new CartLineRequest(40L, 5, false)), "en");

        verify(cartItemRepository).save(existing);
        assertThat(existing.getQuantity()).isEqualTo(5);
        assertThat(existing.isSelected()).isFalse();
    }

    @Test
    void removeSelectedIsANoOpWithoutCartOrIds() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        service.removeSelected(1L, List.of(1L));
        verify(cartItemRepository, never()).deleteByCartIdAndVariantIdIn(anyLong(), any());

        Cart cart = Cart.builder().id(2L).build();
        when(cartRepository.findByUserId(2L)).thenReturn(Optional.of(cart));
        service.removeSelected(2L, List.of());
        verify(cartItemRepository, never()).deleteByCartIdAndVariantIdIn(anyLong(), any());
    }

    @Test
    void removeLineDeletesVariantAndReturnsRemainingCart() {
        Cart cart = Cart.builder().id(3L).build();
        when(cartRepository.findByUserId(9L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(3L)).thenReturn(List.of());

        CartResponse response = service.removeLine(9L, 55L, "en");

        verify(cartItemRepository).deleteByCartIdAndVariantIdIn(3L, List.of(55L));
        assertThat(response.lines()).isEmpty();
    }

    private static User user(Long id) {
        return User.builder().id(id).email("u" + id + "@example.com").fullName("User")
                .role(Role.CUSTOMER).active(true).build();
    }

    static ProductVariant variant(Long id, String size, int stock, int reserved,
                                  BigDecimal basePrice, BigDecimal discountPrice) {
        Product product = Product.builder()
                .id(id + 1000)
                .slug("product-" + id)
                .nameI18n(Map.of("en", "Panjabi", "bn", "পাঞ্জাবি"))
                .descriptionI18n(Map.of("en", "desc"))
                .basePrice(basePrice)
                .discountPrice(discountPrice)
                .active(true)
                .build();
        return ProductVariant.builder()
                .id(id)
                .product(product)
                .sizeLabel(size)
                .sku("SKU-" + id)
                .stockQuantity(stock)
                .reservedQuantity(reserved)
                .active(true)
                .build();
    }
}
