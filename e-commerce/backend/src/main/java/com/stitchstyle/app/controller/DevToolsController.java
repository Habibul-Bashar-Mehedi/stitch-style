package com.stitchstyle.app.controller;

import com.stitchstyle.app.config.CacheEvictionService;
import com.stitchstyle.app.config.CacheNames;
import com.stitchstyle.app.dto.request.SettingRequest;
import com.stitchstyle.app.service.SiteSettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevToolsController {

    private final SiteSettingService siteSettingService;
    private final CacheEvictionService cacheEvictionService;
    private final String instanceId;

    public DevToolsController(SiteSettingService siteSettingService, CacheEvictionService cacheEvictionService,
                              @Value("${server.port:0}") String port) {
        this.siteSettingService = siteSettingService;
        this.cacheEvictionService = cacheEvictionService;
        this.instanceId = port + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("instance", instanceId);
    }

    @PostMapping("/settings")
    public Map<String, String> setting(@RequestBody Map<String, String> body) {
        String key = body.getOrDefault("key", "brand.name");
        String value = body.getOrDefault("value", "Stitch & Style");
        siteSettingService.update(new SettingRequest(key, value, "STRING"), null);
        return Map.of("key", key, "value", value);
    }

    @PostMapping("/cache/evict")
    public void evict() {
        cacheEvictionService.evictNow(CacheNames.CATEGORY_TREE, CacheNames.HOME_CARDS, CacheNames.PRODUCT_LIST,
                CacheNames.PRODUCT_DETAIL, CacheNames.PRODUCT_STOCK, CacheNames.BANNERS, CacheNames.NOTICES,
                CacheNames.SITE_SETTINGS, CacheNames.DELIVERY_AREAS, CacheNames.PAYMENT_METHODS);
    }
}
