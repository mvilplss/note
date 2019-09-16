package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.TestUtil;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/9
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class ThreadPoolDemo extends BaseDemo {

    @Test
    public void xxx() throws Exception{
        CountDownLatch countDownLatch = new CountDownLatch(5);
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        class MyRunnable implements Runnable{
            @Override
            public void run() {
                log.info("starting");
                TestUtil.sleep(3000);
                log.info("ending");
                countDownLatch.countDown();
            }
        }
        MyRunnable runnable = new MyRunnable();
        threadPool.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.error("rejectedExecution "+((FutureTask)r).isDone());
                r.run();
            }
        });
        threadPool.submit(runnable);
        threadPool.submit(runnable);
        threadPool.shutdownNow();
        threadPool.submit(runnable);
        threadPool.submit(runnable);
        threadPool.submit(runnable);
        countDownLatch.await();
    }

    @Test
    public void timer() throws Exception{
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                log.info("1000");
                countDownLatch.countDown();
            }
        },1000);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                log.info("100");
                TestUtil.sleep(10000);
                countDownLatch.countDown();
            }
        },100);
        countDownLatch.await();
    }
}
