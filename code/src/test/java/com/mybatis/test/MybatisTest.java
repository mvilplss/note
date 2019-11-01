package com.mybatis.test;

import com.mybatis.demo.User;
import com.mybatis.demo.UserMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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
    public void jdbcTest() throws Exception{
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test_db", "root", "root");
        PreparedStatement statement = connection.prepareStatement("select * from tb_user");
        statement.execute();
        ResultSet resultSet = statement.getResultSet();
        List<User> users = new ArrayList<>();
        while (resultSet.next()){
            Object id = resultSet.getObject("id");
            Object name = resultSet.getObject("name");
            User user = new User();
            user.setId(Long.valueOf(id.toString()));
            user.setName(name.toString());
            users.add(user);
        }
        System.out.println(users);
        resultSet.close();
        statement.close();
        connection.close();
    }

    @Test
    public void mybatisTest() throws Exception{
        InputStream inputStream = Resources.getResourceAsStream("mybatis/mybatis-config.xml");
        SqlSessionFactory sqlSessionFactory =  new SqlSessionFactoryBuilder().build(inputStream);
        SqlSession sqlSession = sqlSessionFactory.openSession();
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
        List<User> users = userMapper.selectAll();
        System.out.println(users);
    }
}
