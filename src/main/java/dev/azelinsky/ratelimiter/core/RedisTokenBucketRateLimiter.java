package dev.azelinsky.ratelimiter.core;

import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

public final class RedisTokenBucketRateLimiter implements RateLimiter {
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_per_second = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local time = redis.call('TIME')
            local now_ms = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local last_ms = tonumber(state[2])
            if tokens == nil then tokens = capacity last_ms = now_ms end
            local elapsed_ms = math.max(0, now_ms - last_ms)
            local refill_per_ms = refill_per_second / 1000.0
            tokens = math.min(capacity, tokens + elapsed_ms * refill_per_ms)
            local allowed = 0
            local retry_ms = 0
            if tokens >= permits then tokens = tokens - permits allowed = 1
            else retry_ms = math.ceil((permits - tokens) / refill_per_ms) end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now_ms)
            local ttl_ms = math.max(1000, math.ceil((capacity / refill_per_ms) * 2))
            redis.call('PEXPIRE', key, ttl_ms)
            return {allowed, math.floor(tokens), retry_ms}
            """;

    private final RateLimitPolicy policy;
    private final RedisClusterClient client;
    private final StatefulRedisClusterConnection<String,String> connection;
    private final RedisAdvancedClusterAsyncCommands<String,String> commands;

    public RedisTokenBucketRateLimiter(List<String> redisUris, RateLimitPolicy policy) {
        this.policy = policy;
        this.client = RedisClusterClient.create(redisUris.stream().map(RedisURI::create).toList());
        var topologyRefresh = ClusterTopologyRefreshOptions.builder().enablePeriodicRefresh(Duration.ofSeconds(30)).enableAllAdaptiveRefreshTriggers().build();
        client.setOptions(ClusterClientOptions.builder().autoReconnect(true).topologyRefreshOptions(topologyRefresh).build());
        this.connection = client.connect(); this.commands = connection.async();
    }

    @Override public CompletionStage<RateLimitDecision> tryAcquire(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey must not be blank");
        String key = ApiKeyHasher.redisKey(apiKey);
        var future = commands.<List<Object>>eval(TOKEN_BUCKET_SCRIPT, ScriptOutputType.MULTI, new String[]{key}, Long.toString(policy.capacity()), Double.toString(policy.refillTokensPerSecond()), "1");
        return future.thenApply(RedisTokenBucketRateLimiter::toDecision);
    }

    private static RateLimitDecision toDecision(List<Object> raw) {
        if (raw == null || raw.size() != 3) throw new IllegalStateException("unexpected Redis script result: " + raw);
        return new RateLimitDecision(number(raw.get(0)) == 1, number(raw.get(1)), number(raw.get(2)), RateLimitDecision.Source.CENTRAL_REDIS);
    }
    private static long number(Object value) { if (value instanceof Number n) return n.longValue(); return Long.parseLong(value.toString()); }
    @Override public void close() { connection.close(); client.shutdown(); }
}
