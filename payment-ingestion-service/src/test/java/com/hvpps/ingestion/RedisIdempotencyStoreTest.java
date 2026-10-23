package com.hvpps.ingestion;

import com.hvpps.ingestion.idempotency.RedisIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RedisIdempotencyStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void distinguishesNewDuplicateAndUnknownRegistration() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("payments:idempotency:req"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(true, false, null);
        var store = new RedisIdempotencyStore(redis);
        assertThat(store.registerIfAbsent("req")).isTrue();
        assertThat(store.registerIfAbsent("req")).isFalse();
        assertThatThrownBy(() -> store.registerIfAbsent("req")).isInstanceOf(IllegalStateException.class);
    }
}
