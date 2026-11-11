package com.stitchstyle.app.controller.publicapi;

import com.stitchstyle.app.dto.response.HomeResponse;
import com.stitchstyle.app.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
public class HomeController {

    private final SiteSettingService siteSettingService;
    private final BannerService bannerService;
    private final NoticeService noticeService;
    private final CategoryService categoryService;
    private final ProductService productService;

    public HomeController(SiteSettingService siteSettingService, BannerService bannerService,
                          NoticeService noticeService, CategoryService categoryService, ProductService productService) {
        this.siteSettingService = siteSettingService;
        this.bannerService = bannerService;
        this.noticeService = noticeService;
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @GetMapping("/home")
    public HomeResponse home(@RequestParam(required = false) String lang,
                             @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String resolved = LocaleHelper.resolve(lang, acceptLanguage);
        return new HomeResponse(siteSettingService.getAll(), bannerService.active(), noticeService.active(),
                categoryService.homeCards(), productService.latest(resolved));
    }
}
