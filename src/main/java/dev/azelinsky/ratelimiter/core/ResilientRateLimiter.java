package dev.azelinsky.ratelimiter.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ResilientRateLimiter implements RateLimiter {
    private final RateLimiter central; private final RateLimiter fallback; private final Duration timeout; private final int failureThreshold; private final long openNanos;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilNanos = new AtomicLong();

    public ResilientRateLimiter(RateLimiter central, RateLimiter fallback, Duration timeout, int failureThreshold, Duration openDuration) {
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be > 0");
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be > 0");
        if (openDuration.isZero() || openDuration.isNegative()) throw new IllegalArgumentException("openDuration must be > 0");
        this.central = central; this.fallback = fallback; this.timeout = timeout; this.failureThreshold = failureThreshold; this.openNanos = openDuration.toNanos();
    }

    @Override public CompletionStage<RateLimitDecision> tryAcquire(String apiKey) {
        if (System.nanoTime() < circuitOpenUntilNanos.get()) return fallback.tryAcquire(apiKey);
        var result = new CompletableFuture<RateLimitDecision>();
        central.tryAcquire(apiKey).toCompletableFuture().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((decision, error) -> {
            if (error == null) {
                consecutiveFailures.set(0); circuitOpenUntilNanos.set(0); result.complete(decision); return;
            }
            recordFailure();
            fallback.tryAcquire(apiKey).whenComplete((fallbackDecision, fallbackError) -> {
                if (fallbackError == null) result.complete(fallbackDecision); else result.completeExceptionally(fallbackError);
            });
        });
        return result;
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            circuitOpenUntilNanos.set(System.nanoTime() + openNanos); consecutiveFailures.set(0);
        }
    }

    @Override public void close() throws Exception {
        Exception first = null;
        try { central.close(); } catch (Exception e) { first = e; }
        try { fallback.close(); } catch (Exception e) { if (first == null) first = e; else first.addSuppressed(e); }
        if (first != null) throw first;
    }
}
