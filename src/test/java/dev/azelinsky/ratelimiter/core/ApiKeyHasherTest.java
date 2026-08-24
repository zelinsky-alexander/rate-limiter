package dev.azelinsky.ratelimiter.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApiKeyHasherTest {
    @Test void producesStableRedisClusterKeyWithoutPlaintextApiKey() {
        var first = ApiKeyHasher.redisKey("secret-client-key");
        var second = ApiKeyHasher.redisKey("secret-client-key");
        assertEquals(first, second); assertFalse(first.contains("secret-client-key"));
    }
}
