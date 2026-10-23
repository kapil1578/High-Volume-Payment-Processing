package com.hvpps.ingestion.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "hvpps.idempotency.store", havingValue = "redis", matchIfMissing = true)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "payments:idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean registerIfAbsent(String requestId) {
        Boolean added = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + requestId, "1", DEFAULT_TTL);
        if (added == null) throw new IllegalStateException("Redis registration result is unknown");
        return added;
    }
}
