package org.example.other;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class JdkSocketServerNio {
    public static void main(String[] args) throws IOException {
        // 服务器绑定
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        ServerSocket socket = serverSocketChannel.socket();
        socket.bind(new InetSocketAddress(8802));
        // 打开selector
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        final ByteBuffer msg = ByteBuffer.wrap("Hi!\r\n".getBytes());
        while (true) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()){
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                if (selectionKey.isAcceptable()){
                    ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel client = channel.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ, msg.duplicate());
                    System.out.println("接收到客户端请求："+channel);
                    // 响应请求
                    ByteBuffer resp = ByteBuffer.wrap("i am netty, you give me :".getBytes());
                    client.write(resp);
                }
                if (selectionKey.isWritable()){
                    SocketChannel channel = (SocketChannel) selectionKey.channel();
                    ByteBuffer attachment = (ByteBuffer) selectionKey.attachment();
                    while (attachment.hasRemaining()){
                        if (channel.write(attachment)==0){
                            break;
                        }
                    }
//                    channel.close();
                }
            }
        }


    }
}
