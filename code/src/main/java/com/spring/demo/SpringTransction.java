package com.spring.demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/11/5
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class SpringTransction {

    JdbcTemplate jdbcTemplate;

    SpringTransction self;

    @Transactional(propagation = Propagation.REQUIRED)
    public void methodA(){
        boolean aopProxy = AopUtils.isAopProxy(this);
        System.out.println(aopProxy);
        System.out.println(AopUtils.isAopProxy(self));
        self.methodB();
        // do something
    }

    @Transactional(propagation = Propagation.NEVER,rollbackFor = Exception.class)
    public void methodB(){
        // do something
    }




    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SpringTransction getSelf() {
        return self;
    }

    public void setSelf(SpringTransction self) {
        this.self = self;
    }
}
