package util;

import org.junit.Test;

import java.io.UnsupportedEncodingException;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/30
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class EnCodeDemo {


    @Test
    public void xxx() throws UnsupportedEncodingException {
        String a = new String("本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目");
        byte[] bytes = a.getBytes("GBK");
        byte[] gbks = new String(bytes, "GBK").getBytes("UTF-8");
        System.out.println(new String(gbks,"GBK"));

    }
}
