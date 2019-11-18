package com.mybatis.cache;

import cn.hutool.json.JSONObject;
import com.alibaba.fastjson.JSON;
import com.mybatis.demo.User;
import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/11/2
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class RedisCache implements Cache, InitializingObject {
    private String host;
    private int port;
    private String password;
    private Jedis jedis = null;

    public RedisCache(String id) {

    }

    @Override
    public String getId() {
        return "REDIS";
    }

    @Override
    public void putObject(Object key, Object value) {
        jedis.connect();
        try {
            ByteArrayOutputStream keyByteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream keyObjectOutputStream = new ObjectOutputStream(keyByteArrayOutputStream);
            keyObjectOutputStream.writeObject(key);
            ByteArrayOutputStream valueByteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream valueObjectOutputStream = new ObjectOutputStream(valueByteArrayOutputStream);
            valueObjectOutputStream.writeObject(key);
            jedis.set(keyByteArrayOutputStream.toByteArray(), valueByteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }
    }

    @Override
    public Object getObject(Object key) {
        jedis.connect();

        try {
            ByteArrayOutputStream keyByteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream keyObjectOutputStream = new ObjectOutputStream(keyByteArrayOutputStream);
            keyObjectOutputStream.writeObject(key);
            byte[] bytes = jedis.get(keyByteArrayOutputStream.toByteArray());
            if (bytes==null){
                return null;
            }
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            return objectInputStream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }
        return null;
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    @Override
    public void clear() {

    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return new ReentrantReadWriteLock();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Jedis getJedis() {
        return jedis;
    }

    public void setJedis(Jedis jedis) {
        this.jedis = jedis;
    }

    @Override
    public void initialize() throws Exception {
        jedis = new Jedis(host, port);
        jedis.auth(password);
    }
}
