package dev.azelinsky.ratelimiter.core;

import java.util.concurrent.CompletionStage;

public interface RateLimiter extends AutoCloseable {
    CompletionStage<RateLimitDecision> tryAcquire(String apiKey);
    @Override default void close() throws Exception {}
}
