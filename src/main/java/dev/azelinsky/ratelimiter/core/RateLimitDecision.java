package dev.azelinsky.ratelimiter.core;

public record RateLimitDecision(boolean allowed, long remainingTokens, long retryAfterMillis, Source source) {
    public enum Source {
        CENTRAL_REDIS("redis"), LOCAL_FALLBACK("local-fallback");
        private final String headerValue;
        Source(String headerValue) { this.headerValue = headerValue; }
        public String headerValue() { return headerValue; }
    }
}
