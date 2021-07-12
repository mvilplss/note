package org.example.test.java;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class SignletonTest {


    public static class BigClass {
        private static BigClass bigClass;
        private String name1 = "张三1";
        private String name2 = "张三2";
        private String name3 = "张三3";
        private String name4 = "张三4";
        private String name5 = "张三5";
        private String name6 = "张三6";
        private String name7 = "张三7";
        private String name8 = "张三8";
        private String name9 = "张三9";

        public BigClass() {
            name1 = System.currentTimeMillis() + name1;
            Thread.yield();
            name2 = System.currentTimeMillis() + name2;
            Thread.yield();
            name3 = System.currentTimeMillis() + name3;
            Thread.yield();
            name4 = System.currentTimeMillis() + name4;
            Thread.yield();
            name5 = System.currentTimeMillis() + name5;
            Thread.yield();
            name6 = System.currentTimeMillis() + name6;
            Thread.yield();
            name7 = System.currentTimeMillis() + name7;
            Thread.yield();
            name8 = System.currentTimeMillis() + name8;
            Thread.yield();
            name9 = System.currentTimeMillis() + name9;
        }

        public static BigClass getInstance() {
            if (bigClass == null) {
                synchronized (BigClass.class) {
                    if (bigClass == null) {
                        bigClass = new BigClass();
                        return bigClass;
                    }
                }
            }
            return null;
        }
        public static void setNull(){
            bigClass = null;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        while (true){
            CountDownLatch countDownLatch = new CountDownLatch(10000);
            for (int i = 0; i < 10000; i++) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        BigClass bigClass = BigClass.getInstance();
                        if (bigClass != null) {
                            if ("张三1".equals(bigClass.name1) ||
                                    "张三2".equals(bigClass.name2) ||
                                    "张三3".equals(bigClass.name3) ||
                                    "张三4".equals(bigClass.name4) ||
                                    "张三5".equals(bigClass.name5)) {
                                throw new RuntimeException("miracle");
                            }
                            BigClass.setNull();
                        }
                        countDownLatch.countDown();
                    }
                }).start();
            }
            countDownLatch.await();
            System.out.println(1);
        }
    }


}
