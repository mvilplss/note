package org.example.other;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Channels;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JdkSocketServerBio {

    public static void main(String[] args) throws IOException {
        final AtomicInteger threadNum = new AtomicInteger();
        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(10, 50, 6000L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>(100), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"socket-thread-name-"+threadNum.incrementAndGet());
            }
        });
        ServerSocket serverSocket = new ServerSocket(8800);
        Socket accept;
        while (( accept=serverSocket.accept())!=null){
            final Socket finalAccept = accept;
            poolExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(finalAccept.getInputStream()));
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(finalAccept.getOutputStream()));
                        String line = null;
                        while ((line=reader.readLine())!=null){
                            if ("done".equals(line)){
                                writer.close();
                                finalAccept.close();
                                break;
                            }
                            if ("".equals(line)){
                                continue;
                            }
                            String name = Thread.currentThread().getName();
                            writer.println(name+" To Big: "+line.toUpperCase());
                            writer.flush();
                        }
                    }catch (Exception e){
                        // ignore
                    }
                }
            });
        }

    }
}
