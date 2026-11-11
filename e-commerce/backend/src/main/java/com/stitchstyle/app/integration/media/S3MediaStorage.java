package com.stitchstyle.app.integration.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * S3-compatible adapter boundary. Phase 0–2 runs the local adapter; production
 * object storage is a later phase item and must not silently fall back to local.
 */
@Component
@ConditionalOnProperty(name = "app.media.storage", havingValue = "s3")
public class S3MediaStorage implements MediaStorage {

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        throw new UnsupportedOperationException("S3 media storage is not enabled in this phase");
    }

    @Override
    public byte[] load(String key) {
        throw new UnsupportedOperationException("S3 media storage is not enabled in this phase");
    }

    @Override
    public void delete(String key) {
        throw new UnsupportedOperationException("S3 media storage is not enabled in this phase");
    }

    @Override
    public String url(String key) {
        throw new UnsupportedOperationException("S3 media storage is not enabled in this phase");
    }
}
