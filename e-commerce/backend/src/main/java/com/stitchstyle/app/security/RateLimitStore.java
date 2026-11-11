package com.stitchstyle.app.security;

public interface RateLimitStore {
    boolean tryConsume(String key, int limit);
}
