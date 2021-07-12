package org.example.test.java.asyn;

import org.junit.Test;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPool {

    @Test
    public void test_() throws Exception{

    }

    @Test
    public void test_pe() throws Exception{
        ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return null;
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
               throw new Error("xx");
            }
        });
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread());
                Thread.currentThread().interrupt();
            }
        });
        System.in.read();
    }
}
