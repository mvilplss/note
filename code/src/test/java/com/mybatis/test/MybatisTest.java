package com.mybatis.test;

import com.mybatis.demo.User;
import com.mybatis.demo.UserMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/10/25
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class MybatisTest {

    @Test
    public void mybatisTest() throws Exception{
        InputStream inputStream = Resources.getResourceAsStream("mybatis/mybatis-config.xml");
        DefaultSqlSessionFactory sqlSessionFactory = (DefaultSqlSessionFactory) new SqlSessionFactoryBuilder().build(inputStream);
        DefaultSqlSession sqlSession = (DefaultSqlSession) sqlSessionFactory.openSession();
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        System.out.println(userMapper.selectAll());
        User user = new User();
        user.setId(2L);
        user.setName("bb");
        userMapper.updateById(user);
        System.out.println(userMapper.selectAll());
    }
}
