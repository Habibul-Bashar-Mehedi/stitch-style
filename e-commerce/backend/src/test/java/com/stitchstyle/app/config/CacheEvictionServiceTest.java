package com.stitchstyle.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheEvictionServiceTest {

    @Test
    void evictsImmediatelyWhenNoTransactionIsActive() {
        CacheManager manager = new ConcurrentMapCacheManager(CacheNames.CATEGORY_TREE);
        CacheEvictionService service = new CacheEvictionService(manager);
        manager.getCache(CacheNames.CATEGORY_TREE).put("k", "v");

        service.evictAfterCommit(CacheNames.CATEGORY_TREE);

        assertThat(manager.getCache(CacheNames.CATEGORY_TREE).get("k")).isNull();
    }

    @Test
    void defersEvictionUntilAfterCommit() {
        CacheManager manager = new ConcurrentMapCacheManager(CacheNames.PRODUCT_LIST);
        CacheEvictionService service = new CacheEvictionService(manager);
        manager.getCache(CacheNames.PRODUCT_LIST).put("k", "v");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.evictAfterCommit(CacheNames.PRODUCT_LIST);
            assertThat(manager.getCache(CacheNames.PRODUCT_LIST).get("k")).isNotNull();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertThat(manager.getCache(CacheNames.PRODUCT_LIST).get("k")).isNull();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictNowClearsEveryKnownCacheAndIgnoresUnknownOnes() {
        CacheManager manager = new ConcurrentMapCacheManager(CacheNames.BANNERS, CacheNames.NOTICES);
        CacheEvictionService service = new CacheEvictionService(manager);
        manager.getCache(CacheNames.BANNERS).put("k", "v");

        service.evictNow(CacheNames.BANNERS, CacheNames.NOTICES, "noSuchCache");

        assertThat(manager.getCache(CacheNames.BANNERS).get("k")).isNull();
    }
}
