package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import util.JvmUtil;
import util.TestUtil;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/31
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class BaseDemo {
    Long begitTimes;
    public String s(Object obj){
        if (obj==null){
            return "null";
        }
        return obj.toString();
    }
    @Before
    public void before() throws Exception{
        log.debug("pid={}",JvmUtil.getPid());
        begitTimes = System.currentTimeMillis();
    }
    @After
    public void after() throws Exception{
        log.debug("take up time:{} ms",System.currentTimeMillis()-begitTimes);
    }
}
