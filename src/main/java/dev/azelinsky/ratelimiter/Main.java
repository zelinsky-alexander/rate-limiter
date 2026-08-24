package dev.azelinsky.ratelimiter;

import dev.azelinsky.ratelimiter.config.AppConfig;
import dev.azelinsky.ratelimiter.core.LocalTokenBucketRateLimiter;
import dev.azelinsky.ratelimiter.core.RateLimitPolicy;
import dev.azelinsky.ratelimiter.core.RateLimiter;
import dev.azelinsky.ratelimiter.core.RedisTokenBucketRateLimiter;
import dev.azelinsky.ratelimiter.core.ResilientRateLimiter;
import dev.azelinsky.ratelimiter.http.GatewayServer;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        var config = AppConfig.fromEnvironment();
        var policy = new RateLimitPolicy(config.capacity(), config.refillTokensPerSecond());
        var fallbackPolicy = policy.divideAcross(config.fallbackGatewayCount());
        var redisLimiter = new RedisTokenBucketRateLimiter(config.redisUris(), policy);
        var localLimiter = new LocalTokenBucketRateLimiter(fallbackPolicy);
        RateLimiter limiter = new ResilientRateLimiter(redisLimiter, localLimiter, config.redisTimeout(), config.circuitBreakerFailureThreshold(), config.circuitBreakerOpenDuration());
        var server = new GatewayServer(config.port(), limiter);
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            try { server.close(); limiter.close(); } catch (Exception ignored) {}
        }));
        System.out.printf("rate-limiter listening on :%d, redis=%s, capacity=%d, refill=%.3f/s%n", config.port(), config.redisUris(), policy.capacity(), policy.refillTokensPerSecond());
        server.startAndBlock();
    }
}
