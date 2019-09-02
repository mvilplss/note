package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

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
        String x = "https://img.maihaoche.com/e4601bb0-a8b0-43d6-82c5-227819cd1db7.jpg";
        String[] split = x.split("#\\*#");
       log.info(Arrays.toString(split));
       log.info(split[0]);
    }


}
