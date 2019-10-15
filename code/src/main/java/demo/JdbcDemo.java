package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/10/15
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class JdbcDemo extends BaseDemo {

    @Test
    public void xxx() throws Exception{
        Connection connection = DriverManager.getConnection("", "", "");
        System.out.println(connection);


    }
}
