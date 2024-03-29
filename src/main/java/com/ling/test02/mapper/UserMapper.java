package com.ling.test02.mapper;

import com.ling.test02.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    // 在mybatis dao模式下，不能使用方法重载
    boolean addUser(User user);

    int deleteById(Integer userid);

    boolean updateUserById(User user);

    User selectById(@Param("id") Integer id);

    List<User> queryAllUser();

    User checkLogin(String name, String pwd);

    User selectByName(String name);

    List<User> queryByKey(String key);


}
