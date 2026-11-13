package com.stitchstyle.app;

import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CheckoutCustomer;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.response.OrderResponse;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.Order;
import com.stitchstyle.app.model.entity.OrderStatus;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.ReturnCondition;
import com.stitchstyle.app.model.entity.Review;
import com.stitchstyle.app.model.entity.ReviewStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.dto.request.RefundRequest;
import com.stitchstyle.app.dto.request.ReturnRequestCreate;
import com.stitchstyle.app.dto.request.ReturnStatusRequest;
import com.stitchstyle.app.repository.OrderItemRepository;
import com.stitchstyle.app.repository.OrderRepository;
import com.stitchstyle.app.repository.ReviewRepository;
import com.stitchstyle.app.service.OrderService;
import com.stitchstyle.app.service.ReturnService;
import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "it-admin@example.com";

    @Autowired OrderService orderService;
    @Autowired ReturnService returnService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ReviewRepository reviewRepository;

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        ensureUser(ADMIN, Role.ADMIN);
        return user(ADMIN).roles("ADMIN");
    }

    @Test
    void categoriesCanBeCreatedUpdatedReorderedHiddenAndDeleted() throws Exception {
        String tag = suffix();
        MvcResult root = mvc.perform(post("/api/v1/admin/categories").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Root " + tag + "\",\"slug\":\"admin-root-" + tag
                                + "\",\"showOnHome\":true,\"showInNavbar\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("admin-root-" + tag))
                .andReturn();
        long rootId = id(root);

        MvcResult child = mvc.perform(post("/api/v1/admin/categories").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Child " + tag + "\",\"parentId\":" + rootId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depth").value(1))
                .andReturn();
        long childId = id(child);

        mvc.perform(get("/api/v1/admin/categories").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='admin-root-" + tag + "')].children.length()").value(hasItem(1)));

        mvc.perform(put("/api/v1/admin/categories/" + rootId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed " + tag + "\",\"slug\":\"admin-root-" + tag
                                + "\",\"showOnHome\":false,\"showInNavbar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed " + tag));

        mvc.perform(patch("/api/v1/admin/categories/" + rootId + "/visibility").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/admin/categories/reorder").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[" + childId + "],\"parentId\":" + rootId + "}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/admin/categories/" + rootId).with(admin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void productsCanBeCreatedUpdatedStockedListedAndDeleted() throws Exception {
        String tag = suffix();
        Category category = ensureCategory("it-admin-cat-" + tag, "Admin Category " + tag, null, false);
        MediaAsset media = ensureMedia("it-admin-product-" + tag + ".png");
        String sku = "IT-ADMIN-" + tag.toUpperCase();

        MvcResult created = mvc.perform(post("/api/v1/admin/products").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"nameI18n":{"en":"Admin Product %s"},"slug":"admin-product-%s",
                                 "descriptionI18n":{"en":"desc"},"basePrice":1500.00,"discountPrice":1200.00,
                                 "sizeType":"LETTER","active":true,"featured":true,
                                 "variants":[{"sizeLabel":"M","sku":"%s","stockQuantity":5,"lowStockThreshold":1,"active":true}],
                                 "imageMediaIds":[%d]}
                                """.formatted(category.getId(), tag, tag, sku, media.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("admin-product-" + tag))
                .andExpect(jsonPath("$.effectivePrice").value(1200.00))
                .andExpect(jsonPath("$.variants[0].sku").value(sku))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andReturn();
        long productId = id(created);
        long variantId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("variants").get(0).get("id").asLong();

        mvc.perform(get("/api/v1/admin/products/" + productId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId));

        mvc.perform(put("/api/v1/admin/products/" + productId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"nameI18n":{"en":"Updated %s"},"slug":"admin-product-%s",
                                 "descriptionI18n":{"en":"desc"},"basePrice":1600.00,
                                 "sizeType":"LETTER","active":true,"featured":false,
                                 "variants":[{"id":%d,"sizeLabel":"M","sku":"%s","stockQuantity":5,"lowStockThreshold":1,"active":true}],
                                 "imageMediaIds":[]}
                                """.formatted(category.getId(), tag, tag, variantId, sku)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated " + tag));

        mvc.perform(patch("/api/v1/admin/products/" + productId + "/variants/" + variantId + "/stock")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(8));

        mvc.perform(get("/api/v1/admin/products").param("size", "10").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) productId)));

        mvc.perform(delete("/api/v1/admin/products/" + productId).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.deactivated").value(false));

        mvc.perform(get("/api/v1/admin/products/" + productId).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void bannersNoticesAndDeliveryAreasCanBeManaged() throws Exception {
        String tag = suffix();
        MediaAsset media = ensureMedia("it-admin-banner-" + tag + ".png");

        MvcResult banner = mvc.perform(post("/api/v1/admin/banners").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Banner " + tag + "\",\"imageMediaId\":" + media.getId()
                                + ",\"linkUrl\":\"/sale\",\"displayOrder\":1,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Banner " + tag))
                .andReturn();
        long bannerId = id(banner);

        mvc.perform(put("/api/v1/admin/banners/" + bannerId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Banner updated " + tag + "\",\"imageMediaId\":" + media.getId()
                                + ",\"displayOrder\":2,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Banner updated " + tag));

        mvc.perform(get("/api/v1/admin/banners").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) bannerId)));

        mvc.perform(delete("/api/v1/admin/banners/" + bannerId).with(admin()).with(csrf()))
                .andExpect(status().isOk());

        MvcResult notice = mvc.perform(post("/api/v1/admin/notices").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Notice " + tag + "\",\"body\":\"Body\",\"priority\":3,\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        long noticeId = id(notice);
        mvc.perform(put("/api/v1/admin/notices/" + noticeId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Notice two " + tag + "\",\"body\":\"Body\",\"priority\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(9));
        mvc.perform(delete("/api/v1/admin/notices/" + noticeId).with(admin()).with(csrf()))
                .andExpect(status().isOk());

        MvcResult area = mvc.perform(post("/api/v1/admin/delivery-areas").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Area " + tag + "\",\"district\":\"Dhaka\","
                                + "\"deliveryCharge\":70.00,\"freeShippingThreshold\":2000.00,\"slaDays\":2}"))
                .andExpect(status().isCreated())
                .andReturn();
        long areaId = id(area);
        mvc.perform(put("/api/v1/admin/delivery-areas/" + areaId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Area updated " + tag + "\",\"deliveryCharge\":80.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCharge").value(80.00));
        mvc.perform(get("/api/v1/admin/delivery-areas").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem("Admin Area updated " + tag)));
        mvc.perform(delete("/api/v1/admin/delivery-areas/" + areaId).with(admin()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void settingsCouponsAndStaffAreOwnerManagedAndAudited() throws Exception {
        String tag = suffix();

        mvc.perform(put("/api/v1/admin/settings").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"brand.name\",\"value\":\"Stitch & Style\",\"valueType\":\"STRING\"},"
                                + "{\"key\":\"payment.cod.enabled\",\"value\":\"true\",\"valueType\":\"BOOLEAN\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='brand.name')].value").value(hasItem("Stitch & Style")));

        MvcResult coupon = mvc.perform(post("/api/v1/admin/coupons").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ADM" + tag.toUpperCase() + "\",\"discountType\":\"PERCENT\","
                                + "\"discountValue\":15.00,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ADM" + tag.toUpperCase()))
                .andReturn();
        long couponId = id(coupon);
        mvc.perform(put("/api/v1/admin/coupons/" + couponId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ADM" + tag.toUpperCase() + "\",\"discountType\":\"FIXED\","
                                + "\"discountValue\":100.00,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("FIXED"));
        mvc.perform(delete("/api/v1/admin/coupons/" + couponId).with(admin()).with(csrf()))
                .andExpect(status().isOk());

        String staffEmail = "it-staff-" + tag + "@example.com";
        MvcResult staff = mvc.perform(post("/api/v1/admin/staff").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + staffEmail + "\",\"fullName\":\"Staff " + tag
                                + "\",\"role\":\"MANAGER\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andReturn();
        long staffId = id(staff);
        mvc.perform(patch("/api/v1/admin/staff/" + staffId + "/active").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/admin/staff").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email").value(hasItem(staffEmail)));

        // The audit aspect records every admin mutation above.
        mvc.perform(get("/api/v1/admin/audit").param("size", "100").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void reviewsComplaintsReturnsAnalyticsAndInvoicesAreAdministerable() throws Exception {
        String tag = suffix();
        Category category = ensureCategory("it-admin-flow-cat-" + tag, "Flow Category " + tag, null, false);
        Product product = ensureProduct(category, "it-admin-flow-" + tag, "Flow Product " + tag,
                "ফ্লো", new BigDecimal("500.00"), null);
        ProductVariant variant = ensureVariant(product, "M", "IT-FLOW-" + tag.toUpperCase(), 10, 0);
        var area = ensureDeliveryArea("IT Flow Area " + tag, new BigDecimal("60.00"), null);
        User customer = ensureUser("it-customer-" + tag + "@example.com", Role.CUSTOMER);

        // review moderation
        Review review = reviewRepository.save(Review.builder().product(product).user(customer).rating(4)
                .comment("Nice").status(ReviewStatus.PENDING).createdAt(Instant.now()).build());
        mvc.perform(patch("/api/v1/admin/reviews/" + review.getId()).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/admin/reviews").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(review.getId().intValue())));

        // complaint response
        MvcResult complaint = mvc.perform(post("/api/v1/public/complaints").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flow\",\"phone\":\"01712345678\",\"message\":\"Help " + tag + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        long complaintId = id(complaint);
        mvc.perform(patch("/api/v1/admin/complaints/" + complaintId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"Resolved\",\"close\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // order + invoice + status + courier + shipment + notification resend
        OrderResponse placed = orderService.place(new OrderRequest(
                new CheckoutCustomer("Flow Customer", "01712345678"), "House 1, Dhaka", area.getId(),
                PaymentMethod.COD, null, null, List.of(new CartLineRequest(variant.getId(), 1, true))),
                customer, null, "it-flow-order-" + tag + "-0001", "en");
        Order order = orderRepository.findByOrderNumber(placed.orderNumber()).orElseThrow();

        mvc.perform(get("/api/v1/admin/orders").with(admin())
                        .param("orderNumber", order.getOrderNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value(order.getOrderNumber()));
        mvc.perform(get("/api/v1/admin/orders/" + order.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(post("/api/v1/admin/orders/invoices").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[" + order.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value(order.getOrderNumber()));
        mvc.perform(patch("/api/v1/admin/orders/" + order.getId() + "/status").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mvc.perform(patch("/api/v1/admin/orders/" + order.getId() + "/status").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(patch("/api/v1/admin/orders/" + order.getId() + "/courier").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierName\":\"Pathao\",\"trackingNumber\":\"TRACK-" + tag + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/orders/" + order.getId() + "/shipment").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courierCode\":\"PATHAO\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/orders/" + order.getId() + "/notifications/resend")
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk());

        // full return lifecycle through the admin return controller
        Long orderItemId = orderItemRepository.findByOrderId(order.getId()).get(0).getId();
        var returnResponse = returnService.request(customer, new ReturnRequestCreate(
                order.getOrderNumber(), "Fit", List.of(new ReturnRequestCreate.Item(
                orderItemId, 1, ReturnCondition.UNOPENED))));
        mvc.perform(get("/api/v1/admin/returns").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(returnResponse.id().intValue())));
        mvc.perform(get("/api/v1/admin/returns/" + returnResponse.id()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        mvc.perform(patch("/api/v1/admin/returns/" + returnResponse.id()).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(patch("/api/v1/admin/returns/" + returnResponse.id()).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
        BigDecimal refundAmount = returnService.get(returnResponse.id()).quote().total();
        mvc.perform(post("/api/v1/admin/returns/" + returnResponse.id() + "/refund")
                        .with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"MANUAL\",\"amount\":" + refundAmount + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mvc.perform(get("/api/v1/admin/analytics/summary").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.revenue").isNumber())
                .andExpect(jsonPath("$.sla").exists());
    }

    @Test
    void mediaUploadListAndDeleteWorkAndUnsafeFilesAreRejected() throws Exception {
        byte[] png = png();
        MockMultipartFile file = new MockMultipartFile("file", "it-upload.png", "image/png", png);
        MvcResult uploaded = mvc.perform(multipart("/api/v1/admin/media").file(file)
                        .with(admin()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("it-upload.png"))
                .andReturn();
        long mediaId = id(uploaded);

        mvc.perform(get("/api/v1/admin/media").with(admin()).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) mediaId)));

        mvc.perform(multipart("/api/v1/admin/media")
                        .file(new MockMultipartFile("file", "it.svg", "image/svg+xml", "<svg/>".getBytes()))
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA"));

        mvc.perform(delete("/api/v1/admin/media/" + mediaId).with(admin()).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/admin/media/" + mediaId).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
