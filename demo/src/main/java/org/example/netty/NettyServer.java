package org.example.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;

public class NettyServer {
    public static void main(String[] args) throws InterruptedException {
        final EchoServerHandler echoServerHandler = new EchoServerHandler();
        ServerBootstrap bt = new ServerBootstrap();
        EventLoopGroup group = new NioEventLoopGroup();
        bt.group(group).channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(8801))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(echoServerHandler);
                    }
                });
        ChannelFuture future = bt.bind().sync();
        future.channel().closeFuture().sync();
        group.shutdownGracefully().sync();
    }

    @ChannelHandler.Sharable
    static class EchoServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf byteBuf = (ByteBuf) msg;
            String req = byteBuf.toString(CharsetUtil.UTF_8);
            System.out.println(req);
            ctx.write(byteBuf);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
