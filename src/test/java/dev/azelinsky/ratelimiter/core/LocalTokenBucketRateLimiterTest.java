package dev.azelinsky.ratelimiter.core;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class LocalTokenBucketRateLimiterTest {
    @Test void allowsBurstThenRejectsUntilRefill() throws Exception {
        var clock = new AtomicLong();
        var limiter = new LocalTokenBucketRateLimiter(new RateLimitPolicy(2, 1.0), clock::get);
        assertTrue(limiter.tryAcquire("client-a").toCompletableFuture().get().allowed());
        assertTrue(limiter.tryAcquire("client-a").toCompletableFuture().get().allowed());
        assertFalse(limiter.tryAcquire("client-a").toCompletableFuture().get().allowed());
        clock.addAndGet(1_000_000_000L);
        assertTrue(limiter.tryAcquire("client-a").toCompletableFuture().get().allowed());
    }
    @Test void isolatesClients() throws Exception {
        var clock = new AtomicLong();
        var limiter = new LocalTokenBucketRateLimiter(new RateLimitPolicy(1, 1.0), clock::get);
        assertTrue(limiter.tryAcquire("client-a").toCompletableFuture().get().allowed());
        assertFalse(limiter.tryAcquire("client-a").toCompletableFuture().get().allowed());
        assertTrue(limiter.tryAcquire("client-b").toCompletableFuture().get().allowed());
    }
}
