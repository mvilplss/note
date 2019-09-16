package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.TestUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/11
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class LockDemo extends BaseDemo {

    @Test
    public void countdownlatch() throws Exception{
        CountDownLatch countDownLatch = new CountDownLatch(2);
        countDownLatch.countDown();
//        countDownLatch.countDown();
//        countDownLatch.countDown();
//        countDownLatch.countDown();
        long count = countDownLatch.getCount();
        log.info("count{}",count);
        log.info("countDownLatch:{}",countDownLatch);
        countDownLatch.await();

    }

    @Test
    public void readWriteLock() throws Exception{
        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        Lock readLock = readWriteLock.readLock();
        Lock writeLock = readWriteLock.writeLock();

    }


    @Test
    public void xx() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        class Ceo {
            private ReentrantLock reentrantLock = new ReentrantLock();

            public void talk() {
                try {
                    if (reentrantLock.tryLock(4, TimeUnit.SECONDS)) {
                        try {
                            log.info("ceo is talking ...");
                            TestUtil.sleep(5000);
                            log.info("talking is over");
                        } finally {
                            reentrantLock.unlock();
                        }
                    } else {
                        log.error("ceo is busy , see you later");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        Ceo ceo = new Ceo();
        new Thread(() -> {
            ceo.talk();
            countDownLatch.countDown();
        }).start();

        new Thread(() -> {
            ceo.talk();
            countDownLatch.countDown();
        }).start();

        countDownLatch.await();
    }
}
