package dev.azelinsky.ratelimiter.http;

import dev.azelinsky.ratelimiter.core.RateLimiter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public final class GatewayServer implements AutoCloseable {
    private final int port; private final RateLimiter limiter;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();
    private Channel serverChannel;
    public GatewayServer(int port, RateLimiter limiter) { this.port = port; this.limiter = limiter; }
    public void startAndBlock() throws InterruptedException {
        var bootstrap = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class).childOption(ChannelOption.TCP_NODELAY, true).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override protected void initChannel(SocketChannel channel) {
                channel.pipeline().addLast(new HttpServerCodec()).addLast(new HttpObjectAggregator(16 * 1024)).addLast(new RateLimitHttpHandler(limiter));
            }
        });
        serverChannel = bootstrap.bind(port).sync().channel();
        serverChannel.closeFuture().sync();
    }
    @Override public void close() {
        if (serverChannel != null) serverChannel.close();
        workers.shutdownGracefully(); boss.shutdownGracefully();
    }
}
