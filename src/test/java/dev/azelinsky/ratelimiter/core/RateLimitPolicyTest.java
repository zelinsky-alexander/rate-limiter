package dev.azelinsky.ratelimiter.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class RateLimitPolicyTest {
    @Test void dividesFallbackBudgetAcrossGatewayNodes() {
        var divided = new RateLimitPolicy(100, 50.0).divideAcross(3);
        assertEquals(34, divided.capacity());
        assertEquals(50.0 / 3.0, divided.refillTokensPerSecond(), 1e-12);
    }
}
