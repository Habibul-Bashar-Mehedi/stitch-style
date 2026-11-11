package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.ProductRequest;
import com.stitchstyle.app.dto.request.StockAdjustmentRequest;
import com.stitchstyle.app.dto.response.PageResponse;
import com.stitchstyle.app.dto.response.ProductDeletionResponse;
import com.stitchstyle.app.dto.response.ProductResponse;
import com.stitchstyle.app.dto.response.VariantResponse;
import com.stitchstyle.app.service.CurrentUserService;
import com.stitchstyle.app.service.LocaleHelper;
import com.stitchstyle.app.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminProductController {

    private final ProductService productService;
    private final CurrentUserService currentUserService;

    public AdminProductController(ProductService productService, CurrentUserService currentUserService) {
        this.productService = productService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String lang,
                                              @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.adminList(page, size, LocaleHelper.resolve(lang, acceptLanguage));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id, @RequestParam(required = false) String lang,
                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.getById(id, LocaleHelper.resolve(lang, acceptLanguage));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request,
                                  @RequestParam(required = false) String lang,
                                  @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.create(request, LocaleHelper.resolve(lang, acceptLanguage));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request,
                                  @RequestParam(required = false) String lang,
                                  @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return productService.update(id, request, LocaleHelper.resolve(lang, acceptLanguage));
    }

    @DeleteMapping("/{id}")
    public ProductDeletionResponse delete(@PathVariable Long id) {
        boolean deleted = productService.delete(id);
        return new ProductDeletionResponse(deleted, !deleted);
    }

    @PatchMapping("/{id}/variants/{variantId}/stock")
    public VariantResponse stock(@PathVariable Long id, @PathVariable Long variantId,
                                 @Valid @RequestBody StockAdjustmentRequest request) {
        return productService.adjustStock(id, variantId, request.delta(), currentUserService.optional().orElse(null));
    }
}
