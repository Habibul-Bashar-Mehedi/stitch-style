package com.stitchstyle.app.config;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CacheEvictionService {

    private final CacheManager cacheManager;

    public CacheEvictionService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAfterCommit(String... cacheNames) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(cacheNames);
                }
            });
        } else {
            evictNow(cacheNames);
        }
    }

    public void evictNow(String... cacheNames) {
        for (String name : cacheNames) {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }
}
