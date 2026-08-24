package dev.azelinsky.ratelimiter.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record AppConfig(int port, List<String> redisUris, long capacity, double refillTokensPerSecond, Duration redisTimeout, int fallbackGatewayCount, int circuitBreakerFailureThreshold, Duration circuitBreakerOpenDuration) {
    public static AppConfig fromEnvironment() { return from(System.getenv()); }

    static AppConfig from(Map<String, String> env) {
        return new AppConfig(integer(env, "RATE_LIMIT_PORT", 8080), csv(env.getOrDefault("REDIS_URIS", "redis://127.0.0.1:7000,redis://127.0.0.1:7001,redis://127.0.0.1:7002")), positiveLong(env, "RATE_LIMIT_CAPACITY", 100), positiveDouble(env, "RATE_LIMIT_REFILL_PER_SECOND", 50.0), Duration.ofMillis(positiveLong(env, "REDIS_TIMEOUT_MS", 50)), positiveInt(env, "FALLBACK_GATEWAY_COUNT", 3), positiveInt(env, "CIRCUIT_BREAKER_FAILURE_THRESHOLD", 3), Duration.ofMillis(positiveLong(env, "CIRCUIT_BREAKER_OPEN_MS", 1000)));
    }

    private static List<String> csv(String value) {
        var result = Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (result.isEmpty()) throw new IllegalArgumentException("REDIS_URIS must contain at least one URI");
        return result;
    }
    private static int integer(Map<String, String> env, String name, int d) { return Integer.parseInt(env.getOrDefault(name, Integer.toString(d))); }
    private static int positiveInt(Map<String, String> env, String name, int d) { int v = integer(env, name, d); if (v <= 0) throw new IllegalArgumentException(name + " must be > 0"); return v; }
    private static long positiveLong(Map<String, String> env, String name, long d) { long v = Long.parseLong(env.getOrDefault(name, Long.toString(d))); if (v <= 0) throw new IllegalArgumentException(name + " must be > 0"); return v; }
    private static double positiveDouble(Map<String, String> env, String name, double d) { double v = Double.parseDouble(env.getOrDefault(name, Double.toString(d))); if (!Double.isFinite(v) || v <= 0.0) throw new IllegalArgumentException(name + " must be finite and > 0"); return v; }
}
