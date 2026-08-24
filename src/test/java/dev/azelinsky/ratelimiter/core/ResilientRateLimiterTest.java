package dev.azelinsky.ratelimiter.core;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
class ResilientRateLimiterTest {
    @Test void fallsBackWhenCentralLimiterFails() throws Exception {
        RateLimiter failing = key -> CompletableFuture.failedFuture(new RuntimeException("redis unavailable"));
        RateLimiter fallback = key -> CompletableFuture.completedFuture(new RateLimitDecision(true, 4, 0, RateLimitDecision.Source.LOCAL_FALLBACK));
        var limiter = new ResilientRateLimiter(failing, fallback, Duration.ofMillis(50), 1, Duration.ofSeconds(1));
        var result = limiter.tryAcquire("client-a").toCompletableFuture().get();
        assertTrue(result.allowed()); assertEquals(RateLimitDecision.Source.LOCAL_FALLBACK, result.source());
    }
}
