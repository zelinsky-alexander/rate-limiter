package dev.azelinsky.ratelimiter.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class LocalTokenBucketRateLimiter implements RateLimiter {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private final RateLimitPolicy policy;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LocalTokenBucketRateLimiter(RateLimitPolicy policy) { this(policy, System::nanoTime); }
    LocalTokenBucketRateLimiter(RateLimitPolicy policy, LongSupplier nanoTime) { this.policy = policy; this.nanoTime = nanoTime; }

    @Override public CompletionStage<RateLimitDecision> tryAcquire(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("apiKey must not be blank"));
        long now = nanoTime.getAsLong();
        var bucket = buckets.computeIfAbsent(apiKey, ignored -> new Bucket(policy.capacity(), now));
        return CompletableFuture.completedFuture(bucket.acquire(now, policy));
    }
    int bucketCount() { return buckets.size(); }

    private static final class Bucket {
        private double tokens; private long lastRefillNanos;
        private Bucket(long capacity, long nowNanos) { tokens = capacity; lastRefillNanos = nowNanos; }
        private synchronized RateLimitDecision acquire(long nowNanos, RateLimitPolicy policy) {
            long elapsed = Math.max(0L, nowNanos - lastRefillNanos);
            tokens = Math.min(policy.capacity(), tokens + elapsed * (policy.refillTokensPerSecond() / NANOS_PER_SECOND));
            lastRefillNanos = nowNanos;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new RateLimitDecision(true, (long)Math.floor(tokens), 0, RateLimitDecision.Source.LOCAL_FALLBACK);
            }
            long retryMillis = Math.max(1L, (long)Math.ceil((1.0 - tokens) / policy.refillTokensPerSecond() * 1000.0));
            return new RateLimitDecision(false, 0, retryMillis, RateLimitDecision.Source.LOCAL_FALLBACK);
        }
    }
}
