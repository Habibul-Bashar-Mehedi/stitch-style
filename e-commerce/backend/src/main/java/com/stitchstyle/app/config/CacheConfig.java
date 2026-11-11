package com.stitchstyle.app.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "false", matchIfMissing = true)
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        // Phase 0–2 uses an in-process cache so the cache contract and invalidation
        // tests run without external infrastructure. Phase 3 replaces this with the
        // shared Redis cache required by IN-2; ehcache.xml remains the local option.
        return new ConcurrentMapCacheManager(
                CacheNames.CATEGORY_TREE,
                CacheNames.HOME_CARDS,
                CacheNames.PRODUCT_LIST,
                CacheNames.PRODUCT_DETAIL,
                CacheNames.PRODUCT_STOCK,
                CacheNames.BANNERS,
                CacheNames.NOTICES,
                CacheNames.SITE_SETTINGS,
                CacheNames.DELIVERY_AREAS,
                CacheNames.PAYMENT_METHODS);
    }
}
