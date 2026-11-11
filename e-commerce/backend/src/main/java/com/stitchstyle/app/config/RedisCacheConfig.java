package com.stitchstyle.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true")
public class RedisCacheConfig {

    @Bean
    @Primary
    CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues();
        Map<String, RedisCacheConfiguration> caches = Map.of(
                CacheNames.CATEGORY_TREE, defaults.entryTtl(Duration.ofMinutes(30)),
                CacheNames.HOME_CARDS, defaults.entryTtl(Duration.ofMinutes(30)),
                CacheNames.PRODUCT_LIST, defaults.entryTtl(Duration.ofMinutes(10)),
                CacheNames.PRODUCT_DETAIL, defaults.entryTtl(Duration.ofMinutes(10)),
                CacheNames.PRODUCT_STOCK, defaults.entryTtl(Duration.ofSeconds(10)),
                CacheNames.BANNERS, defaults.entryTtl(Duration.ofMinutes(5)),
                CacheNames.NOTICES, defaults.entryTtl(Duration.ofMinutes(5)),
                CacheNames.SITE_SETTINGS, defaults.entryTtl(Duration.ofMinutes(15)),
                CacheNames.DELIVERY_AREAS, defaults.entryTtl(Duration.ofMinutes(30)),
                CacheNames.PAYMENT_METHODS, defaults.entryTtl(Duration.ofMinutes(5)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(caches)
                .build();
    }
}
