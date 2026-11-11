package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.response.*;
import com.stitchstyle.app.service.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
public class CatalogController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final DeliveryAreaService deliveryAreaService;
    private final SiteSettingService siteSettingService;

    public CatalogController(CategoryService categoryService, ProductService productService,
                             DeliveryAreaService deliveryAreaService, SiteSettingService siteSettingService) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.deliveryAreaService = deliveryAreaService;
        this.siteSettingService = siteSettingService;
    }

    @GetMapping("/categories/tree")
    public List<CategoryResponse> tree() {
        return categoryService.tree(false);
    }

    @GetMapping("/categories/{slug}")
    public CategoryResponse category(@PathVariable String slug) {
        return categoryService.getBySlug(slug);
    }

    @GetMapping("/categories/{slug}/products")
    public PageResponse<ProductResponse> categoryProducts(
            @PathVariable String slug,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sizeLabel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.search(slug, q, minPrice, maxPrice, sizeLabel, page, size, sort,
                LocaleHelper.resolve(lang, acceptLanguage));
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sizeLabel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.search(null, q, minPrice, maxPrice, sizeLabel, page, size, sort,
                LocaleHelper.resolve(lang, acceptLanguage));
    }

    @GetMapping("/products/{slug}")
    public ProductResponse product(@PathVariable String slug,
                                   @RequestParam(required = false) String lang,
                                   @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.getBySlug(slug, LocaleHelper.resolve(lang, acceptLanguage));
    }

    @GetMapping("/products/{slug}/related")
    public List<ProductResponse> related(@PathVariable String slug,
                                         @RequestParam(required = false) String lang,
                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.related(slug, LocaleHelper.resolve(lang, acceptLanguage));
    }

    @GetMapping("/delivery-areas")
    public List<DeliveryAreaResponse> deliveryAreas() {
        return deliveryAreaService.active();
    }

    @GetMapping("/payment-methods")
    public List<PaymentMethodResponse> paymentMethods() {
        return siteSettingService.paymentMethods();
    }
}
