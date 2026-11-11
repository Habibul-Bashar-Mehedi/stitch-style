package com.stitchstyle.app.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate redis;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryConsume(String key, int limit) {
        String redisKey = "rate:" + key + ":" + (System.currentTimeMillis() / 60000);
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1L) redis.expire(redisKey, Duration.ofMinutes(2));
        return count != null && count <= limit;
    }
}
