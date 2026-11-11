package com.stitchstyle.app;

import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CategoryRequest;
import com.stitchstyle.app.dto.request.CheckoutCustomer;
import com.stitchstyle.app.dto.request.ComplaintRequest;
import com.stitchstyle.app.dto.request.ComplaintResponseRequest;
import com.stitchstyle.app.dto.request.CouponRequest;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.request.RefundRequest;
import com.stitchstyle.app.dto.request.ReturnRequestCreate;
import com.stitchstyle.app.dto.request.ReturnStatusRequest;
import com.stitchstyle.app.dto.request.ReviewModerationRequest;
import com.stitchstyle.app.dto.request.ReviewRequest;
import com.stitchstyle.app.dto.response.ComplaintResponse;
import com.stitchstyle.app.dto.response.OrderResponse;
import com.stitchstyle.app.dto.response.ReturnResponse;
import com.stitchstyle.app.dto.response.ReviewResponse;
import com.stitchstyle.app.model.entity.*;
import com.stitchstyle.app.service.CategoryService;
import com.stitchstyle.app.service.AnalyticsService;
import com.stitchstyle.app.service.ComplaintService;
import com.stitchstyle.app.service.CouponService;
import com.stitchstyle.app.service.OrderService;
import com.stitchstyle.app.service.ReturnService;
import com.stitchstyle.app.service.ReviewService;
import com.stitchstyle.app.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired DeliveryAreaRepository deliveryAreaRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired CategoryService categoryService;
    @Autowired CacheManager cacheManager;
    @Autowired OrderService orderService;
    @Autowired UserRepository userRepository;
    @Autowired CouponService couponService;
    @Autowired ReturnService returnService;
    @Autowired ReviewService reviewService;
    @Autowired ComplaintService complaintService;
    @Autowired AnalyticsService analyticsService;

    private ProductVariant variant;
    private DeliveryArea area;

    @BeforeEach
    void seed() {
        Category category = categoryRepository.findBySlug("men").orElseGet(() -> {
            Category c = new Category();
            c.setName("Men");
            c.setSlug("men");
            c.setSizeType(SizeType.LETTER);
            c.setActive(true);
            return categoryRepository.save(c);
        });
        Product product = productRepository.findBySlug("test-panjabi").orElseGet(() -> {
            Product p = new Product();
            p.setCategory(category);
            p.setNameI18n(java.util.Map.of("en", "Test Panjabi"));
            p.setDescriptionI18n(java.util.Map.of("en", "Test description"));
            p.setSlug("test-panjabi");
            p.setBasePrice(new BigDecimal("1000.00"));
            p.setSizeType(SizeType.LETTER);
            p.setActive(true);
            return productRepository.save(p);
        });
        variant = variantRepository.findBySku("TEST-M").orElseGet(() -> {
            ProductVariant v = new ProductVariant();
            v.setProduct(product);
            v.setSizeLabel("M");
            v.setSku("TEST-M");
            v.setStockQuantity(2);
            v.setReservedQuantity(0);
            v.setLowStockThreshold(1);
            v.setActive(true);
            return variantRepository.save(v);
        });
        area = deliveryAreaRepository.findAll().stream().findFirst().orElseGet(() -> {
            DeliveryArea a = new DeliveryArea();
            a.setName("Dhaka");
            a.setDeliveryCharge(new BigDecimal("60.00"));
            a.setActive(true);
            return deliveryAreaRepository.save(a);
        });
        variant.setStockQuantity(2);
        variant.setReservedQuantity(0);
        variantRepository.save(variant);
    }

    @Test
    void codOrderIsIdempotentAndDecrementsStock() throws Exception {
        String key = "8e03978e-40d5-43e8-bc93-6894a57f9324";
        String payload = """
                {
                  "customer": {"name": "Nusrat Jahan", "phone": "01712345678"},
                  "shippingAddress": "House 12, Road 7, Dhanmondi, Dhaka 1205",
                  "deliveryAreaId": %d,
                  "paymentMethod": "COD",
                  "items": [{"variantId": %d, "quantity": 1, "selected": true}]
                }
                """.formatted(area.getId(), variant.getId());

        MvcResult created = mvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .cookie(new Cookie("guest_id", "guest-test"))
                        .with(csrf())
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/track/")))
                .andReturn();

        String orderNumber = objectMapper.readTree(created.getResponse().getContentAsString()).get("orderNumber").asText();
        String token = objectMapper.readTree(created.getResponse().getContentAsString()).get("publicToken").asText();
        assertThat(orderRepository.findByOrderNumber(orderNumber)).isPresent();
        assertThat(variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);

        mvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .cookie(new Cookie("guest_id", "guest-test"))
                        .with(csrf())
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.orderNumber").value(orderNumber));

        mvc.perform(get("/api/v1/public/orders/track/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void adminCanCancelAPendingOrderAndStockIsRestored() {
        int before = variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity();
        OrderRequest request = new OrderRequest(new CheckoutCustomer("Cancel Test", "01712345678"),
                "House 1, Road 1, Dhaka", area.getId(), PaymentMethod.COD, null, null,
                List.of(new CartLineRequest(variant.getId(), 1, true)));
        String key = "cancel-" + java.util.UUID.randomUUID();
        OrderResponse created = orderService.place(request, null, "guest-" + key, key, "en");
        Order order = orderRepository.findByOrderNumber(created.orderNumber()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(before - 1);

        orderService.updateStatus(order.getId(), OrderStatus.CANCELLED, "Admin cancel");

        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(before);
    }

    @Test
    void couponReturnReviewAndComplaintFlowsWork() {
        User customer = userRepository.findByEmailIgnoreCase("customer-test@example.com").orElseGet(() ->
                userRepository.save(User.builder().fullName("Test Customer").email("customer-test@example.com")
                        .role(Role.CUSTOMER).active(true).build()));
        String code = "TEST" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        couponService.create(new CouponRequest(code, "PERCENT", new BigDecimal("10.00"), BigDecimal.ZERO, 10,
                null, null, true));
        OrderRequest request = new OrderRequest(new CheckoutCustomer("Test Customer", "01712345678"),
                "House 1, Road 1, Dhaka", area.getId(), PaymentMethod.COD, null, code,
                List.of(new CartLineRequest(variant.getId(), 1, true)));
        OrderResponse created = orderService.place(request, customer, null, "user-order-" + code, "en");
        Order order = orderRepository.findByOrderNumber(created.orderNumber()).orElseThrow();
        assertThat(order.getCouponDiscount()).isGreaterThan(BigDecimal.ZERO);
        orderService.updateStatus(order.getId(), OrderStatus.SHIPPED, null);
        orderService.updateStatus(order.getId(), OrderStatus.DELIVERED, null);
        Long orderItemId = orderItemRepository.findByOrderId(order.getId()).get(0).getId();
        ReturnResponse returnResponse = returnService.request(customer, new ReturnRequestCreate(
                order.getOrderNumber(), "Fit issue", List.of(new ReturnRequestCreate.Item(
                        orderItemId, 1, ReturnCondition.UNOPENED))));
        returnService.advance(returnResponse.id(), new ReturnStatusRequest(ReturnStatus.APPROVED, null));
        returnService.advance(returnResponse.id(), new ReturnStatusRequest(ReturnStatus.RECEIVED, null));
        ReturnResponse refunded = returnService.refund(returnResponse.id(), new RefundRequest("MANUAL", null));
        assertThat(refunded.status()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(variantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);

        ReviewResponse review = reviewService.submit(customer, "test-panjabi", new ReviewRequest(5, "Great"));
        reviewService.moderate(review.id(), new ReviewModerationRequest(ReviewStatus.APPROVED));
        assertThat(reviewService.approved("test-panjabi")).hasSize(1);

        ComplaintResponse complaint = complaintService.create(new ComplaintRequest("Test Customer", "01712345678",
                order.getOrderNumber(), "Delivery question"));
        ComplaintResponse responded = complaintService.respond(complaint.id(),
                new ComplaintResponseRequest("We have contacted the courier.", true));
        assertThat(responded.status()).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(analyticsService.summary(null, null, true).orders()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void adminReadEndpointsAreReachableByAdmin() throws Exception {
        for (String path : new String[]{
                "/api/v1/admin/analytics/summary",
                "/api/v1/admin/staff",
                "/api/v1/admin/audit",
                "/api/v1/admin/coupons",
                "/api/v1/admin/complaints",
                "/api/v1/admin/returns",
                "/api/v1/admin/settings",
                "/api/v1/admin/banners",
                "/api/v1/admin/notices",
                "/api/v1/admin/delivery-areas",
                "/api/v1/admin/orders"
        }) {
            mvc.perform(get(path).with(user("admin@example.com").roles("ADMIN"))).andExpect(status().isOk());
        }
    }

    @Test
    void categoryCreateEvictsCacheAfterCommit() {
        var cache = cacheManager.getCache(CacheNames.CATEGORY_TREE);
        cache.put("visible", List.of());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        categoryService.create(new CategoryRequest("Test " + suffix, "test-" + suffix, null, null, null,
                SizeType.LETTER, 99, true, false, false));
        assertThat(cache.get("visible")).isNull();
    }

    @Test
    void secondOrderForLastUnitIsRejected() throws Exception {
        ProductVariant fresh = variantRepository.findById(variant.getId()).orElseThrow();
        fresh.setStockQuantity(1);
        variantRepository.saveAndFlush(fresh);
        variant = fresh;
        String payload = """
                {
                  "customer": {"name": "Nusrat Jahan", "phone": "01712345678"},
                  "shippingAddress": "House 12, Road 7, Dhanmondi, Dhaka 1205",
                  "deliveryAreaId": %d,
                  "paymentMethod": "COD",
                  "items": [{"variantId": %d, "quantity": 1, "selected": true}]
                }
                """.formatted(area.getId(), variant.getId());

        mvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "8e03978e-40d5-43e8-bc93-6894a57f0001")
                        .cookie(new Cookie("guest_id", "guest-last-unit"))
                        .with(csrf())
                        .content(payload))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "8e03978e-40d5-43e8-bc93-6894a57f0002")
                        .cookie(new Cookie("guest_id", "guest-last-unit"))
                        .with(csrf())
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }
}
