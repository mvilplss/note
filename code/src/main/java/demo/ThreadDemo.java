package demo;


import cn.hutool.core.util.ReflectUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.TestUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

    @Test
    public void ss() throws IOException {
        class Aba{
            int i = 0;
            public void a(){
                  i++;
                  a();

            }
        }
        Runnable runnable = () -> {
            Aba aba = new Aba();
            try {
                aba.a();
            }finally {
                log.info("sof:{}",aba.i);
            }
        };
        Thread thread = new Thread(null, runnable, "mythread", 1024+1024);
        thread.start();
        System.in.read();
    }

    @Test
    public void printOrderThread2() throws Exception {
        BlockingQueue q1 = new ArrayBlockingQueue(1);
        BlockingQueue q2 = new ArrayBlockingQueue(1);
        BlockingQueue q3 = new ArrayBlockingQueue(1);
        q1.put(new Object());
        new Thread(() -> {
            try {
                while (q1.take() != null) {
                    log.info(s(0));
                    q2.put(new Object());
                }
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> {
            try {
                while (q2.take() != null) {
                    log.info(s(1));
                    q3.put(new Object());
                }
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> {
            try {
                while (q3.take() != null) {
                    log.info(s(2));
                    q1.put(new Object());
                }
            } catch (Exception e) {
            }
        }).start();

        System.in.read();
    }

    @Test
    public void printOrderThread1() throws Exception {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (atomicInteger) {
                    try {
                        while (true) {
                            if (atomicInteger.get() % 3 == 0) {
                                log.info(s(0));
                                atomicInteger.incrementAndGet();
                                atomicInteger.notifyAll();
                            } else {
                                atomicInteger.wait();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (atomicInteger) {
                    try {
                        while (true) {
                            if (atomicInteger.get() % 3 == 1) {
                                log.info(s(1));
                                atomicInteger.incrementAndGet();
                                atomicInteger.notifyAll();
                            } else {
                                atomicInteger.wait();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (atomicInteger) {
                    try {
                        while (true) {
                            if (atomicInteger.get() % 3 == 2) {
                                log.info(s(2));
                                atomicInteger.incrementAndGet();
                                atomicInteger.notifyAll();
                            } else {
                                atomicInteger.wait();
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }).start();

        System.in.read();
    }

    @Test
    public void interrupted() throws Exception {
        Thread thread = null;
        Thread.interrupted();// 判断是否被阻断并清除阻断状态，设置为false。
        thread.isInterrupted();// 单纯的判断阻断状态。
        thread.interrupt();// 对当前线程发起阻断请求。
    }

    @Test
    public void threadState() throws Exception {
        // 通过引擎启动来演示线程的状态转换
        class Engine {
            synchronized void start() {
                try {
                    TestUtil.sleep(500);
                    wait(1000);
                    TestUtil.sleep(2000);
                } catch (Exception e) {
                }
            }
        }
        Engine engine = new Engine();
        Runnable runnable = engine::start;
        // 创建演示目标线程
        Thread thread = new Thread(runnable);
        thread.setName("targetThread");
        // 启动干扰线程，触发同步阻塞
        new Thread(runnable).start();
        // 获取线程状态
        new Thread(() -> {
            Thread.State oldState = null;
            while (true) {
                Thread.State state = thread.getState();
                if (oldState != state) {
                    oldState = state;
                    log.info(thread.getName() + ":" + state);
                }
            }
        }).start();
        thread.start();
        System.in.read();
    }


    // -XX:+PrintFlagsFinal -XX:MaxDirectMemorySize=512
    // -XX:NativeMemoryTracking=detail -Xmx4g -Xss180k
    @Test
    public void stackSize() throws Exception {
        class Door {
            int deepNum;

            public void openDoor() {
                deepNum++;
                if (deepNum < 10000) {
                    openDoor();
                }
            }
        }
        Door door = new Door();
        Thread thread = new Thread(null, () -> {
            log.info("running...");
            door.openDoor();
        }, "xxxxxxx", 1024 * 512);

        thread.start();
        Field stackSizeField = Thread.class.getDeclaredField("stackSize");
        stackSizeField.setAccessible(true);
        Object o = stackSizeField.get(thread);
        log.info("stackSize:" + s(o));
        thread.join();
        log.error("exp:{}", door.deepNum);
    }

    @Test
    public void dcl() throws Exception {

        class Girl {
            private String name;
            private Integer age;

            public Girl() {
                TestUtil.sleep(1);
                name = "lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1lucy1";
                TestUtil.sleep(1);
                age = 20;
            }
        }
        class GirlFactory {
            private Girl girl = null;

            public Girl getOne() {
                if (girl == null) {
                    synchronized (GirlFactory.class) {
                        girl = new Girl();
                        return girl;
                    }
                }
                return girl;
            }
        }
        while (true) {
            GirlFactory girlFactory = new GirlFactory();
            CyclicBarrier cyclicBarrier = new CyclicBarrier(30);
            for (int i = 0; i < 30; i++) {
                new Thread(() -> {
                    try {
                        log.info("is ready ");
                        cyclicBarrier.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Girl girl = girlFactory.getOne();
                    log.info(girl.name + " " + girl.age);
                    if (girl.age != 20) {
                        throw new RuntimeException();
                    }

                }).start();
            }
            TestUtil.sleep(1000);
        }
    }


    // 可见性
    @Test
    public void visibility() throws Exception {
        class EngineService {
            private boolean stop = false;

            public void startEngine() {
                log.info("引擎开始运行...");
                while (!stop) {
                    // 永动机模式
                }
                log.info("引擎停止运行！");
            }

            public void stopEngine() {
                log.info("发出关闭引擎指令！");
                stop = true;
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
    private int a = 0, b = 0, x = 0, y = 0;

    @Test
    public void instructionRearrangement() throws Exception {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            Thread thread1 = new Thread(() -> {
                // 有可能发生重排，即 先执行 x = b,再执行 a = 1
                a = 1;
                x = b;
            });
            Thread thread2 = new Thread(() -> {
                // 有可能发生重排，即先执行 y = a,再执行 b = 1;
                b = 1;
                y = a;
            });
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();
            /**
             * 如果没有指令重排，输出的可以结果为:a=1,b=1,x=0,y=1 ; a=1,b=1,x=1,y=0
             * 但实际上有可能会输出:a=1,b=1,x=0,y=0;
             */
            if (x == 0 && y == 0) {
                log.info("\t" + i + ":" + a + " " + b + " " + x + " " + y);
                break;
            }
            a = b = x = y = 0;
        }
    }
}
