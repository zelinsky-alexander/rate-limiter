package dev.azelinsky.ratelimiter.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ApiKeyHasher {
    private static final HexFormat HEX = HexFormat.of();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new ExceptionInInitializerError(e); }
    });
    private ApiKeyHasher() {}
    static String redisKey(String apiKey) {
        var digest = SHA_256.get(); digest.reset();
        var hash = HEX.formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        return "rl:{" + hash + "}";
    }
}
