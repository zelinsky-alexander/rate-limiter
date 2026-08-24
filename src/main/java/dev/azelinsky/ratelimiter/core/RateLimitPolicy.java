package dev.azelinsky.ratelimiter.core;

public record RateLimitPolicy(long capacity, double refillTokensPerSecond) {
    public RateLimitPolicy {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (!Double.isFinite(refillTokensPerSecond) || refillTokensPerSecond <= 0.0) throw new IllegalArgumentException("refillTokensPerSecond must be finite and > 0");
    }
    public RateLimitPolicy divideAcross(int gatewayCount) {
        if (gatewayCount <= 0) throw new IllegalArgumentException("gatewayCount must be > 0");
        long localCapacity = Math.max(1L, (capacity + gatewayCount - 1L) / gatewayCount);
        return new RateLimitPolicy(localCapacity, refillTokensPerSecond / gatewayCount);
    }
}
