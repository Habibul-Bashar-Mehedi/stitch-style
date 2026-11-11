package com.stitchstyle.app;

import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CategoryRequest;
import com.stitchstyle.app.dto.request.CheckoutCustomer;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.request.ProductRequest;
import com.stitchstyle.app.dto.request.VariantRequest;
import com.stitchstyle.app.dto.response.CategoryResponse;
import com.stitchstyle.app.dto.response.ProductResponse;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.DeliveryArea;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.SizeType;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.DeliveryAreaRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.ProductRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.UserRepository;
import com.stitchstyle.app.service.CategoryService;
import com.stitchstyle.app.service.OrderService;
import com.stitchstyle.app.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the admin delete buttons actually work: never-sold products are removed,
 * sold products are archived, and a collection can be deleted once its products
 * are gone (the case that used to return 409 forever).
 */
@Testcontainers
@SpringBootTest
class CatalogDeletionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CategoryService categoryService;
    @Autowired ProductService productService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired DeliveryAreaRepository deliveryAreaRepository;
    @Autowired UserRepository userRepository;

    @Test
    void deletesNeverSoldProductThenTheEmptyCategory() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryResponse gender = categoryService.create(new CategoryRequest(
                "Deletion Men " + suffix, null, null, null, null, SizeType.LETTER, 0, true, false, false));
        CategoryResponse leaf = categoryService.create(new CategoryRequest(
                "Deletion Shirt " + suffix, null, null, gender.id(), null, SizeType.LETTER, 0, true, false, false));
        ProductResponse product = createProduct(leaf.id(), suffix);

        assertThatThrownBy(() -> categoryService.delete(leaf.id()))
                .hasMessageContaining("active product");

        assertThat(productService.delete(product.id())).isTrue();
        assertThat(productRepository.findById(product.id())).isEmpty();
        assertThat(variantRepository.findByProductIdOrderBySizeLabelAsc(product.id())).isEmpty();

        categoryService.delete(leaf.id());
        assertThat(categoryRepository.findById(leaf.id())).isEmpty();
    }

    @Test
    void soldProductsAreArchivedSoTheCategoryCanStillBeDeleted() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CategoryResponse gender = categoryService.create(new CategoryRequest(
                "Sold Men " + suffix, null, null, null, null, SizeType.LETTER, 0, true, false, false));
        CategoryResponse leaf = categoryService.create(new CategoryRequest(
                "Sold Shirt " + suffix, null, null, gender.id(), null, SizeType.LETTER, 0, true, false, false));
        ProductResponse product = createProduct(leaf.id(), suffix);
        Long variantId = variantRepository.findByProductIdOrderBySizeLabelAsc(product.id()).get(0).getId();

        placeOrder(variantId, suffix);

        assertThat(productService.delete(product.id())).isFalse();
        assertThat(productRepository.findById(product.id()).orElseThrow().isActive()).isFalse();

        categoryService.delete(leaf.id());

        Category archive = categoryRepository.findBySlug("archived-products").orElseThrow();
        assertThat(archive.isActive()).isFalse();
        assertThat(productRepository.findById(product.id()).orElseThrow().getCategory().getId())
                .isEqualTo(archive.getId());
        assertThat(categoryRepository.findById(leaf.id())).isEmpty();
        assertThat(orderRepository.findAll()).isNotEmpty();
    }

    private ProductResponse createProduct(Long categoryId, String suffix) {
        String sku = "DEL-" + suffix.toUpperCase();
        return productService.create(new ProductRequest(
                categoryId,
                Map.of("en", "Deletion Product " + suffix),
                "deletion-product-" + suffix,
                Map.of("en", "Delete me"),
                new BigDecimal("500.00"),
                null,
                SizeType.LETTER,
                null, null, null, null,
                true,
                false,
                List.of(new VariantRequest(null, "M", sku, 5, 1, null, true)),
                List.of()), "en");
    }

    private void placeOrder(Long variantId, String suffix) {
        DeliveryArea area = deliveryAreaRepository.findAll().stream().findFirst().orElseGet(() -> {
            DeliveryArea created = new DeliveryArea();
            created.setName("Deletion Dhaka " + suffix);
            created.setDeliveryCharge(new BigDecimal("60.00"));
            created.setActive(true);
            return deliveryAreaRepository.save(created);
        });
        User customer = userRepository.findByEmailIgnoreCase("deletion-" + suffix + "@example.com").orElseGet(() ->
                userRepository.save(User.builder().fullName("Deletion Customer")
                        .email("deletion-" + suffix + "@example.com").role(Role.CUSTOMER).active(true).build()));
        orderService.place(new OrderRequest(
                new CheckoutCustomer("Deletion Customer", "01712345678"),
                "House 1, Road 1, Dhaka",
                area.getId(),
                PaymentMethod.COD,
                null,
                null,
                List.of(new CartLineRequest(variantId, 1, true))), customer, null,
                "deletion-order-" + suffix, "en");
    }
}
