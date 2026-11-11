package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CartPriceRequest;
import com.stitchstyle.app.dto.response.CartLineResponse;
import com.stitchstyle.app.dto.response.CartResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.repository.CartItemRepository;
import com.stitchstyle.app.repository.CartRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductVariantRepository variantRepository, UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse price(CartPriceRequest request, String lang) {
        List<CartLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (CartLineRequest line : request.items()) {
            ProductVariant variant = variantRepository.findById(line.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + line.variantId()));
            Product product = variant.getProduct();
            if (!variant.isActive() || !product.isActive()) {
                throw new ResourceNotFoundException("Product is no longer available: " + line.variantId());
            }
            BigDecimal unit = unitPrice(product, variant);
            int quantity = Math.max(1, line.quantity());
            boolean stockChanged = variant.available() < quantity;
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            BigDecimal base = product.getBasePrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal effective = unit.multiply(BigDecimal.valueOf(quantity));
            if (base.compareTo(effective) > 0) discount = discount.add(base.subtract(effective));
            lines.add(new CartLineResponse(variant.getId(), product.getId(), product.getSlug(),
                    resolve(product.getNameI18n(), lang), variant.getSizeLabel(), quantity,
                    line.selected() == null || line.selected(), unit, lineTotal, variant.available(),
                    false, stockChanged));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        return new CartResponse(lines, subtotal, BigDecimal.ZERO.setScale(2), discount,
                subtotal.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId, String lang) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) return empty(lang);
        List<CartLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (CartItem item : cartItemRepository.findByCartIdOrderByIdAsc(cart.getId())) {
            ProductVariant variant = item.getVariant();
            Product product = variant.getProduct();
            BigDecimal unit = unitPrice(product, variant);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            if (item.isSelected()) {
                subtotal = subtotal.add(lineTotal);
                BigDecimal base = product.getBasePrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                if (base.compareTo(lineTotal) > 0) discount = discount.add(base.subtract(lineTotal));
            }
            lines.add(new CartLineResponse(variant.getId(), product.getId(), product.getSlug(),
                    resolve(product.getNameI18n(), lang), variant.getSizeLabel(), item.getQuantity(),
                    item.isSelected(), unit, lineTotal, variant.available(), false,
                    variant.available() < item.getQuantity()));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        return new CartResponse(lines, subtotal, BigDecimal.ZERO.setScale(2),
                discount.setScale(2, RoundingMode.HALF_UP), subtotal);
    }

    @Transactional
    public CartResponse replace(Long userId, List<CartLineRequest> lines, String lang) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart created = new Cart();
            created.setUser(user);
            return cartRepository.save(created);
        });
        cartItemRepository.deleteByCartId(cart.getId());
        for (CartLineRequest line : lines) {
            addLine(cart, line, false);
        }
        return getCart(userId, lang);
    }

    @Transactional
    public CartResponse merge(Long userId, List<CartLineRequest> lines, String lang) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart created = new Cart();
            created.setUser(user);
            return cartRepository.save(created);
        });
        for (CartLineRequest line : lines) {
            addLine(cart, line, true);
        }
        return getCart(userId, lang);
    }

    @Transactional
    public void removeSelected(Long userId, Collection<Long> variantIds) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null || variantIds == null || variantIds.isEmpty()) return;
        cartItemRepository.deleteByCartIdAndVariantIdIn(cart.getId(), variantIds);
    }

    @Transactional
    public CartResponse removeLine(Long userId, Long variantId, String lang) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart != null) {
            cartItemRepository.deleteByCartIdAndVariantIdIn(cart.getId(), List.of(variantId));
        }
        return getCart(userId, lang);
    }

    private void addLine(Cart cart, CartLineRequest request, boolean merge) {
        ProductVariant variant = variantRepository.findById(request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + request.variantId()));
        if (!variant.isActive() || !variant.getProduct().isActive()) {
            throw new ResourceNotFoundException("Product is no longer available: " + request.variantId());
        }
        int requested = Math.max(1, request.quantity());
        int capped = Math.min(requested, Math.max(0, variant.available()));
        if (capped == 0) throw new ResourceNotFoundException("Size is out of stock: " + request.variantId());
        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId()).orElse(null);
        if (item == null) {
            item = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(capped)
                    .selected(request.selected() == null || request.selected())
                    .build();
        } else {
            int quantity = merge ? Math.max(item.getQuantity(), capped) : capped;
            item.setQuantity(Math.min(quantity, Math.max(0, variant.available())));
            if (request.selected() != null) item.setSelected(request.selected());
        }
        cartItemRepository.save(item);
    }

    private BigDecimal unitPrice(Product product, ProductVariant variant) {
        if (variant.getPriceOverride() != null) return variant.getPriceOverride();
        if (product.getDiscountPrice() != null) return product.getDiscountPrice();
        return product.getBasePrice();
    }

    private String resolve(Map<String, String> values, String lang) {
        if (values == null) return "";
        String key = lang == null ? "en" : lang;
        return values.getOrDefault(key, values.getOrDefault("en", values.values().stream().findFirst().orElse("")));
    }

    private CartResponse empty(String lang) {
        return new CartResponse(List.of(), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }
}
