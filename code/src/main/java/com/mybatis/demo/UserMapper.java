package com.mybatis.demo;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/10/25
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public interface UserMapper {

    @Select("select * from tb_user")
    List<User> selectAll();

    User selectById(Long id);

    @Update("update tb_user set name=#{name} where id = #{id}")
    int updateById(User user);

}
