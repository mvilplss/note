package com.util.code;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import util.TestUtil;

import java.net.URL;
import java.util.List;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/10/26
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class JikeArtical {
    @Test
    public void get() throws Exception{
        RemoteWebDriver driver = null;
        try {
            Capabilities capabilities = new DesiredCapabilities();
            String remoteDriverUrl = "http://localhost:9515";
            driver = new RemoteWebDriver(new URL(remoteDriverUrl),capabilities);
            TestUtil.sleep(30000);
            for (int i = 5640; i < 16000; i++) {
                driver.get("https://time.geekbang.org/column/article/"+i);
                TestUtil.sleep(3000);
                String pageSource = driver.getPageSource();
                if (pageSource.contains("技术领导力300讲")){
                    System.out.println("https://time.geekbang.org/column/article/"+i);
                }
            }
            System.out.println(driver.getPageSource());
        } catch (Exception e) {
            log.error("getDriver error:{}",e);
        }finally {
            TestUtil.sleep(2000);
            driver.quit();
        }
    }
}
