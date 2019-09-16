package demo;


import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.TestUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/6
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class ThreadDemo extends BaseDemo {
//-XX:+PrintFlagsFinal -XX:MaxDirectMemorySize=512
    // -XX:NativeMemoryTracking=detail -Xmx4g -Xss180k
    @Test
    public void xxx() throws Exception{
        class Door{
            int deepNum;
            public void openDoor(){
                deepNum++;
                if (deepNum<10000){
                    openDoor();
                }
            }
        }
        Door door = new Door();
        Thread thread = new Thread(null,() -> {
            log.info("running...");
            door.openDoor();
        },"xxxxxxx",1024*512);

        thread.start();
        Field stackSizeField = Thread.class.getDeclaredField("stackSize");
        stackSizeField.setAccessible(true);
        Object o = stackSizeField.get(thread);
        log.info("stackSize:"+s(o));
        thread.join();
        log.error("exp:{}",door.deepNum);
    }


    // 可见性
    @Test
    public void visibility() throws Exception{
        class EngineService{
            private boolean stop = false;
            public  void startEngine(){
                log.info("引擎开始运行...");
                while (!stop){
                    // 永动机模式
                }
                log.info("引擎停止运行！");
            }
            public  void stopEngine(){
                log.info("发出关闭引擎指令！");
                stop=true;
            }
            // 如果不用volatile，可以通过stop的同步getter,setter来实现可见性。
        }
        // 两个线程启动关闭引擎
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EngineService engineService = new EngineService();
        Thread startEngineThread = new Thread(() -> {
            engineService.startEngine();
            countDownLatch.countDown();
        });
        Thread stopEngineThread = new Thread(() -> {
            engineService.stopEngine();
            countDownLatch.countDown();
        });
        startEngineThread.start();
        TestUtil.sleep(10);
        stopEngineThread.start();
        countDownLatch.await();
    }

    // 指令重排
    private int a = 0, b = 0,x=0,y=0;
    @Test
    public void instructionRearrangement() throws Exception{
        for (int i=0;i<Integer.MAX_VALUE;i++){
            Thread thread1 = new Thread(() -> {
                // 有可能发生重排，即 先执行 x = b,再执行 a = 1
                a=1;
                x=b;
            });
            Thread thread2 = new Thread(() -> {
                // 有可能发生重排，即先执行 y = a,再执行 b = 1;
                b=1;
                y=a;
            });
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();
            /**
             * 如果没有指令重排，输出的可以结果为:a=1,b=1,x=0,y=1 ; a=1,b=1,x=1,y=0
             * 但实际上有可能会输出:a=1,b=1,x=0,y=0;
             */
            if (x==0&&y==0){
                log.info("\t"+i+":"+a+" "+b+" "+x+" "+y);
                break;
            }
            a = b =  x = y = 0;
        }
    }
}
