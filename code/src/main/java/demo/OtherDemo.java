package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import util.JvmUtil;
import util.TestUtil;

import java.util.Arrays;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/2
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class OtherDemo extends BaseDemo{

    @Test
    public void xxx() throws Exception{
        String a = "你好";
        System.out.println(JvmUtil.getJvmAddress("你好"));
        System.out.println(JvmUtil.getJvmAddress(a));
        TestUtil.sleep(100000);
    }

    @Test
    public void string() throws Exception{
        String a =new String( "abc").intern();
        String b = "a"+"b"+"c";
        String c = b.intern();

        System.out.println(a==b);
        System.out.println(a==c);
        System.out.println(b==c);
        System.out.println(JvmUtil.getJvmAddress(a));
        System.out.println(JvmUtil.getJvmAddress(b));
        System.out.println(JvmUtil.getJvmAddress(c));
    }

    @Test
    public void xxxx() throws Exception{
        System.out.println("hello lll");
    }

}
