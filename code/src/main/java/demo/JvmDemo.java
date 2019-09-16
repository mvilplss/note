package demo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.TestUtil;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/9
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class JvmDemo extends BaseDemo{
    @Test
    public void finalizeInvoke() throws Exception{
        @Data
        class Rabbish{
            private String name;
            private String type;

            @Override
            protected void finalize() throws Throwable {
                super.finalize();
                log.warn("i am rabbish named {},type is {},i am dead",name,type);
                TestUtil.sleep(10000);
                log.warn("haha ");
            }
        }

        Rabbish rabbish = new Rabbish();
        rabbish.setName("废纸箱");
        rabbish.setType("干垃圾");
        rabbish=null;// help gc
        System.gc();
        TestUtil.sleep(3000);
    }

    @Test
    public void hook() throws Exception{
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                TestUtil.sleep(100000);
            }
        });
        thread.setDaemon(true);
        thread.start();
        Thread hook = new Thread(new Runnable() {
            @Override
            public void run() {
                log.info("jvm is close");
                TestUtil.sleep(5000);
                int i = Runtime.getRuntime().availableProcessors();
                log.info("i:{}",i);
            }
        });
        Runtime.getRuntime().addShutdownHook(hook);
        System.exit(0);
    }

}
