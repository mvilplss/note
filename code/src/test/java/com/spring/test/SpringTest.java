package com.spring.test;

import com.spring.demo.SpringTransction;
import demo.other.bean.BigBag;
import demo.other.bean.LittleBag;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import util.TestUtil;

import java.util.Arrays;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/11/4
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class SpringTest {

    @Test
    public void xxx() throws Exception{
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring.xml");
        String[] beanDefinitionNames = context.getBeanDefinitionNames();
        System.out.println(Arrays.toString(beanDefinitionNames));
        SpringTransction bean = context.getBean(SpringTransction.class);
        System.out.println(bean.getClass());
        Thread.sleep(10000000);
        bean.methodA();

    }


    @Test
    public void 循环依赖() throws Exception{
        Class<BigBag> bigBagClass = BigBag.class;
        BigBag bigBag = bigBagClass.newInstance();
        Class<LittleBag> littleBagClass = LittleBag.class;
        LittleBag littleBag = littleBagClass.newInstance();
        bigBag.say();
        littleBag.say();

        bigBagClass.getDeclaredField("littleBag").set(bigBag,littleBag);
        littleBagClass.getDeclaredField("bigBag").set(littleBag,bigBag);
        bigBag.say();
        littleBag.say();

    }
}
