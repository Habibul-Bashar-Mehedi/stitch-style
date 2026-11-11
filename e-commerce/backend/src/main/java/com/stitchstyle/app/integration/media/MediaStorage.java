package com.stitchstyle.app.integration.media;

public interface MediaStorage {
    void store(String key, byte[] bytes, String contentType);
    byte[] load(String key);
    void delete(String key);
    String url(String key);
}
