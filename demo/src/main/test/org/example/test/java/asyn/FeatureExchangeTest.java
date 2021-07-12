package org.example.test.java.asyn;

import com.alibaba.fastjson.parser.Feature;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

public class FeatureExchangeTest {
    @Test
    public void test_() throws Exception{
        CompletableFuture<String> send = send();
        System.out.println("执行完毕！");
        String s = send.get();
        System.out.println("结果："+s);
    }

    public CompletableFuture<String> send(){
        CompletableFuture<String> feature = new CompletableFuture<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    try {
                        System.out.println(i);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                feature.complete("hello feature !");
            }
        }).start();
        return feature;
    }
}
