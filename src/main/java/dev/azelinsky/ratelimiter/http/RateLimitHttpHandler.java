package dev.azelinsky.ratelimiter.http;

import dev.azelinsky.ratelimiter.core.RateLimitDecision;
import dev.azelinsky.ratelimiter.core.RateLimiter;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

public final class RateLimitHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final String API_KEY_HEADER = "X-API-Key";
    private final RateLimiter limiter;
    public RateLimitHttpHandler(RateLimiter limiter) { this.limiter = limiter; }

    @Override protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        var path = new QueryStringDecoder(request.uri()).path();
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (request.method() != HttpMethod.GET) { write(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"method_not_allowed\"}", keepAlive, null); return; }
        if ("/health".equals(path)) { write(ctx, HttpResponseStatus.OK, "{\"status\":\"ok\"}", keepAlive, null); return; }
        if (!"/limited".equals(path)) { write(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"not_found\"}", keepAlive, null); return; }
        var apiKey = request.headers().get(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) { write(ctx, HttpResponseStatus.UNAUTHORIZED, "{\"error\":\"missing_api_key\"}", keepAlive, null); return; }
        limiter.tryAcquire(apiKey).whenComplete((decision, error) -> ctx.executor().execute(() -> {
            if (error != null) write(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "{\"error\":\"rate_limiter_unavailable\"}", keepAlive, null);
            else if (decision.allowed()) write(ctx, HttpResponseStatus.OK, "{\"status\":\"backend_reached\"}", keepAlive, decision);
            else write(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, "{\"error\":\"rate_limited\"}", keepAlive, decision);
        }));
    }

    private static void write(ChannelHandlerContext ctx, HttpResponseStatus status, String json, boolean keepAlive, RateLimitDecision decision) {
        var content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        if (decision != null) {
            response.headers().set("X-RateLimit-Remaining", decision.remainingTokens());
            response.headers().set("X-RateLimit-Source", decision.source().headerValue());
            if (!decision.allowed()) response.headers().set(HttpHeaderNames.RETRY_AFTER, Math.max(1L, (decision.retryAfterMillis() + 999L) / 1000L));
        }
        if (keepAlive) { response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE); ctx.writeAndFlush(response); }
        else ctx.writeAndFlush(response).addListener(future -> ctx.close());
    }
}
