package com.stitchstyle.app;

import com.stitchstyle.app.model.entity.Banner;
import com.stitchstyle.app.model.entity.Category;
import com.stitchstyle.app.model.entity.Coupon;
import com.stitchstyle.app.model.entity.MediaAsset;
import com.stitchstyle.app.model.entity.Notice;
import com.stitchstyle.app.model.entity.Product;
import com.stitchstyle.app.model.entity.ProductVariant;
import com.stitchstyle.app.model.entity.Review;
import com.stitchstyle.app.model.entity.ReviewStatus;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.SiteSetting;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.BannerRepository;
import com.stitchstyle.app.repository.CouponRepository;
import com.stitchstyle.app.repository.NoticeRepository;
import com.stitchstyle.app.repository.ReviewRepository;
import com.stitchstyle.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired BannerRepository bannerRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired ReviewRepository reviewRepository;

    private String slug;
    private Category root;
    private Category leaf;
    private Product product;
    private ProductVariant largeVariant;
    private MediaAsset media;

    @BeforeEach
    void seed() {
        slug = "it-pub-" + suffix();
        root = ensureCategory("it-public-root-" + slug, "Public Root " + slug, null, true);
        leaf = ensureCategory("it-panjabi-" + slug, "Panjabi " + slug, root, false);
        product = ensureProduct(leaf, "it-product-" + slug, "Integration Panjabi " + slug,
                "ইন্টিগ্রেশন পাঞ্জাবি " + slug, new BigDecimal("1200.00"), new BigDecimal("999.00"));
        ensureVariant(product, "M", "IT-M-" + slug, 3, 2);
        largeVariant = ensureVariant(product, "L", "IT-L-" + slug, 10, 0);
        media = ensureMedia("it-media-" + slug + ".png");
        attachImage(product, media);

        if (siteSettingRepository.findBySettingKey("brand.name").isEmpty()) {
            siteSettingRepository.save(SiteSetting.builder().settingKey("brand.name")
                    .settingValue("Stitch & Style").valueType("STRING").build());
        }
        if (bannerRepository.findByActiveTrueOrderByDisplayOrderAsc().isEmpty()) {
            bannerRepository.save(Banner.builder().title("Banner " + slug).imageMedia(media)
                    .displayOrder(0).active(true).build());
        }
        if (noticeRepository.findByActiveTrueOrderByPriorityDesc().isEmpty()) {
            noticeRepository.save(Notice.builder().title("Notice " + slug).body("Body")
                    .priority(5).active(true).build());
        }
        if (couponRepository.findByCodeIgnoreCase("IT10" + slug.toUpperCase()).isEmpty()) {
            couponRepository.save(Coupon.builder().code("IT10" + slug.toUpperCase())
                    .discountType("PERCENT").discountValue(new BigDecimal("10.00"))
                    .minSubtotal(BigDecimal.ZERO).usedCount(0).active(true).build());
        }
        User reviewer = ensureUser("it-reviewer-" + slug + "@example.com", Role.CUSTOMER);
        if (reviewRepository.findByProductIdAndUserId(product.getId(), reviewer.getId()).isEmpty()) {
            reviewRepository.save(Review.builder().product(product).user(reviewer).rating(5)
                    .comment("Excellent").status(ReviewStatus.APPROVED).verifiedPurchase(false)
                    .createdAt(Instant.now()).build());
        }
        ensureDeliveryArea("IT Dhaka " + slug, new BigDecimal("60.00"), new BigDecimal("2000.00"));
    }

    @Test
    void homeAggregatesSettingsBannersNoticesCardsAndLatestProducts() throws Exception {
        mvc.perform(get("/api/v1/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings['brand.name']").value("Stitch & Style"))
                .andExpect(jsonPath("$.banners").isArray())
                .andExpect(jsonPath("$.notices").isArray())
                .andExpect(jsonPath("$.cards").isArray())
                .andExpect(jsonPath("$.latestProducts").isArray());

        mvc.perform(get("/api/v1/public/home?lang=bn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestProducts[?(@.slug=='" + product.getSlug()
                        + "')].name").value(hasItem("ইন্টিগ্রেশন পাঞ্জাবি " + slug)));
    }

    @Test
    void categoryTreeAndDetailExposeTheSeededHierarchy() throws Exception {
        mvc.perform(get("/api/v1/public/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='" + root.getSlug() + "')].children[*].slug")
                        .value(hasItem(leaf.getSlug())));

        mvc.perform(get("/api/v1/public/categories/" + root.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(root.getName()))
                .andExpect(jsonPath("$.children[*].slug").value(hasItem(leaf.getSlug())));

        mvc.perform(get("/api/v1/public/categories/does-not-exist-" + slug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void productSearchSupportsTextPriceSizeSortAndPaging() throws Exception {
        mvc.perform(get("/api/v1/public/products")
                        .param("q", "Integration Panjabi " + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value(product.getSlug()))
                .andExpect(jsonPath("$.content[0].effectivePrice").value(999.00))
                .andExpect(jsonPath("$.content[0].variants.length()").value(2));

        mvc.perform(get("/api/v1/public/products")
                        .param("minPrice", "900").param("maxPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug").value(hasItem(product.getSlug())));

        mvc.perform(get("/api/v1/public/products").param("sizeLabel", "L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug").value(hasItem(product.getSlug())));

        mvc.perform(get("/api/v1/public/products")
                        .param("q", "Integration Panjabi " + slug)
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));

        mvc.perform(get("/api/v1/public/categories/" + leaf.getSlug() + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug").value(hasItem(product.getSlug())));
    }

    @Test
    void productDetailAndRelatedAreLocalisedAndExcludeTheCurrentProduct() throws Exception {
        Product sibling = ensureProduct(leaf, "it-sibling-" + slug, "Sibling Panjabi " + slug,
                "সহোদর পাঞ্জাবি", new BigDecimal("1500.00"), null);

        mvc.perform(get("/api/v1/public/products/" + product.getSlug()).param("lang", "bn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ইন্টিগ্রেশন পাঞ্জাবি " + slug))
                .andExpect(jsonPath("$.images.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.variants[*].sku").value(hasItem("IT-L-" + slug)));

        mvc.perform(get("/api/v1/public/products/" + product.getSlug() + "/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(hasItem(sibling.getSlug())))
                .andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.not(hasItem(product.getSlug()))));

        mvc.perform(get("/api/v1/public/products/missing-" + slug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deliveryAreasAndPaymentMethodsArePublic() throws Exception {
        mvc.perform(get("/api/v1/public/delivery-areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem("IT Dhaka " + slug)));

        mvc.perform(get("/api/v1/public/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='COD')].enabled").value(hasItem(true)));
    }

    @Test
    void publicCartPricingUsesDiscountsAndFlagsStockChanges() throws Exception {
        String body = """
                {"items":[{"variantId":%d,"quantity":2,"selected":true}]}
                """.formatted(largeVariant.getId());

        mvc.perform(post("/api/v1/public/cart/price").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(999.00))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(1998.00))
                .andExpect(jsonPath("$.discountTotal").value(402.00))
                .andExpect(jsonPath("$.subtotal").value(1998.00));

        mvc.perform(post("/api/v1/public/cart/price").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"variantId\":99999999,\"quantity\":1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void couponValidationAppliesAndRejectsUnknownCodes() throws Exception {
        String code = "IT10" + slug.toUpperCase();
        mvc.perform(post("/api/v1/public/coupons/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"subtotal\":1000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.discount").value(100.00));

        mvc.perform(post("/api/v1/public/coupons/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE\",\"subtotal\":1000.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUPON"));
    }

    @Test
    void complaintsCanBeFiledPubliclyAndAreValidated() throws Exception {
        mvc.perform(post("/api/v1/public/complaints").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nusrat","phone":"01712345678","orderNumber":"ORD-1","message":"Where is my order?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+8801712345678"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.overdue").value(false));

        mvc.perform(post("/api/v1/public/complaints").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nusrat\",\"phone\":\"123\",\"message\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void approvedReviewsAreListedAndSubmissionRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/public/products/" + product.getSlug() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].userDisplayName").value("C***"));

        mvc.perform(post("/api/v1/me/products/" + product.getSlug() + "/reviews").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Nice\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void sitemapAndCsrfEndpointsRespond() throws Exception {
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString(product.getSlug())));

        mvc.perform(get("/api/v1/public/csrf"))
                .andExpect(status().isNoContent());
    }

    @Test
    void robotsIsServedAsAStaticResource() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk());
    }

    @Test
    void missingMediaFileReturnsNotFoundNotAnInternalError() throws Exception {
        mvc.perform(get("/media/does-not-exist.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
