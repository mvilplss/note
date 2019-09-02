package demo;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Scanner;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/30
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class NetDemo {

    @Test
    public void socket(){
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("time-A.timefreq.bldrdoc.gov",13),1000);
            socket.setSoTimeout(1000);
            InputStream inputStream = socket.getInputStream();
            Scanner scanner = new Scanner(inputStream);
            while (scanner.hasNext()){
                System.out.println(scanner.next());
                Thread.sleep(1000);
            }
            scanner.close();
            inputStream.close();
            socket.close();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void xxx() throws UnknownHostException {
        InetAddress byName = InetAddress.getByName("time-A.timefreq.bldrdoc.gov");
        System.out.println(byName.getHostName());
        System.out.println(byName.getHostAddress());
        System.out.println(InetAddress.getLocalHost());

        System.out.println(Arrays.toString(InetAddress.getAllByName("time-A.timefreq.bldrdoc.gov")));
    }

    // nio
    @Test
    public void nio() throws IOException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("time-A.timefreq.bldrdoc.gov", 13));
        ByteBuffer dst = ByteBuffer.allocate(1024);
        int len = socketChannel.read(dst);
        System.out.println(new String(dst.array(),0,len));

        int write = socketChannel.write(dst);


    }
    // nio
    @Test
    public void nioClient() throws IOException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("localhost", 8899));
        ByteBuffer dst = ByteBuffer.allocate(1024);
        dst.putInt(1);
        int write = socketChannel.write(dst);
        socketChannel.finishConnect();
        socketChannel.close();


    }
    // nio
    @Test
    public void nioServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("localhost",8899));
        SocketChannel accept = server.accept();
        ByteBuffer dst = ByteBuffer.allocate(1024);
        int len = accept.read(dst);
        System.out.println(dst.getInt());

    }


}
