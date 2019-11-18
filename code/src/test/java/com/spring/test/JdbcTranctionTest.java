package com.spring.test;

import com.BaseTest;
import org.junit.Test;
import util.TestUtil;

import java.sql.*;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/11/5
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class JdbcTranctionTest extends BaseTest {

    public Connection getConnection(int level) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/test_db?useSSL=false", "root", "root");
        connection.setTransactionIsolation(level);
        connection.setAutoCommit(false);
        return connection;
    }

    public void colseConnection(Connection connection) throws SQLException {
        connection.commit();
        connection.close();
    }

    @Test
    public void readUncommit() throws Exception{
        Thread insert = new Thread(() -> {
            Connection connection = null;
            try {
                connection = getConnection(Connection.TRANSACTION_READ_UNCOMMITTED);
                Statement statement = connection.createStatement();
                statement.execute("insert tb_user values (4,'d')");
                TestUtil.sleep(1000);
                connection.rollback();
                colseConnection(connection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread select = new Thread(() -> {
            TestUtil.sleep(300);
            Connection connection = null;
            try {
                connection = getConnection(Connection.TRANSACTION_READ_UNCOMMITTED);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select * from tb_user");
                while (resultSet.next()){
                    System.out.println(resultSet.getString(2));
                }
                colseConnection(connection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        insert.start();
        select.start();
        insert.join();
        select.join();
    }

    @Test
    public void readRepeatable() throws Exception{
        Thread insert = new Thread(() -> {
            Connection connection = null;
            try {
                TestUtil.sleep(400);
                connection = getConnection(Connection.TRANSACTION_READ_UNCOMMITTED);
                Statement statement = connection.createStatement();
                statement.execute("insert tb_user values (4,'d')");
                colseConnection(connection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread select = new Thread(() -> {
            Connection connection = null;
            try {
                connection = getConnection(Connection.TRANSACTION_SERIALIZABLE);
                TestUtil.sleep(1000);
                ResultSet resultSet = connection.createStatement().executeQuery("select * from tb_user");
                while (resultSet.next()){
                    System.out.println(resultSet.getString(2));
                }
                colseConnection(connection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        insert.start();
        select.start();
        insert.join();
        select.join();
        Connection connection = null;
        try {
            connection = getConnection(Connection.TRANSACTION_READ_UNCOMMITTED);
            Statement statement = connection.createStatement();
            statement.execute("delete from tb_user where id=4");
            colseConnection(connection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void readCommited() throws Exception{
        Thread insert = new Thread(() -> {
            Connection connection = null;
            try {
                connection = getConnection(Connection.TRANSACTION_READ_UNCOMMITTED);
                Statement statement = connection.createStatement();
                statement.execute("insert tb_user values (4,'d')");
                TestUtil.sleep(2000);
                connection.rollback();
                colseConnection(connection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread select = new Thread(() -> {
            TestUtil.sleep(1000);
            Connection connection = null;
            try {
                connection = getConnection(Connection.TRANSACTION_READ_COMMITTED);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select * from tb_user");
                while (resultSet.next()){
                    System.out.println(resultSet.getString(2));
                }
                colseConnection(connection);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        insert.start();
        select.start();
        insert.join();
        select.join();
    }
}
