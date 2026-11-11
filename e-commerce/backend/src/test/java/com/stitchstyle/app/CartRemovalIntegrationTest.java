package com.stitchstyle.app;

import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.SizeType;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.CategoryRepository;
import com.stitchstyle.app.repository.ProductRepository;
import com.stitchstyle.app.repository.ProductVariantRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A signed-in shopper must be able to remove cart lines, including the last one.
 * Removing the last line used to send an empty replace payload, which the
 * @NotEmpty validation rejected, so the client reloaded the old cart.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CartRemovalIntegrationTest {

    private static final String EMAIL = "cart-remove@example.com";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired UserRepository userRepository;

    private Long variantId;

    @BeforeEach
    void seed() {
        userRepository.findByEmailIgnoreCase(EMAIL).orElseGet(() ->
                userRepository.save(User.builder().fullName("Cart Remover").email(EMAIL)
                        .role(Role.CUSTOMER).active(true).build()));

        Category category = categoryRepository.findBySlug("cart-removal-category").orElseGet(() -> {
            Category created = new Category();
            created.setName("Cart removal");
            created.setSlug("cart-removal-category");
            created.setSizeType(SizeType.LETTER);
            created.setActive(true);
            return categoryRepository.save(created);
        });
        Product product = productRepository.findBySlug("cart-removal-product").orElseGet(() -> {
            Product created = new Product();
            created.setCategory(category);
            created.setNameI18n(Map.of("en", "Cart removal product"));
            created.setDescriptionI18n(Map.of("en", "Cart removal product"));
            created.setSlug("cart-removal-product");
            created.setBasePrice(new BigDecimal("500.00"));
            created.setSizeType(SizeType.LETTER);
            created.setActive(true);
            return productRepository.save(created);
        });
        variantId = variantRepository.findBySku("CART-REMOVE-M").orElseGet(() -> {
            ProductVariant created = new ProductVariant();
            created.setProduct(product);
            created.setSizeLabel("M");
            created.setSku("CART-REMOVE-M");
            created.setStockQuantity(5);
            created.setReservedQuantity(0);
            created.setLowStockThreshold(1);
            created.setActive(true);
            return variantRepository.save(created);
        }).getId();
    }

    @Test
    void removingTheLastCartLineWorksForASignedInUser() throws Exception {
        addOneLine();

        mvc.perform(delete("/api/v1/me/cart/{variantId}", variantId)
                        .with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0));
    }

    @Test
    void replacingWithAnEmptyListClearsTheCart() throws Exception {
        addOneLine();

        mvc.perform(put("/api/v1/me/cart")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0));
    }

    private void addOneLine() throws Exception {
        mvc.perform(put("/api/v1/me/cart")
                        .with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"variantId\":" + variantId
                                + ",\"quantity\":1,\"selected\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));
    }
}
