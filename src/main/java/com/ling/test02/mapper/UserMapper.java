package com.ling.test02.mapper;

import com.ling.test02.entity.User;

import java.util.List;

public interface UserMapper {

    // 在mybatis dao模式下，不能使用方法重载
    boolean addUser(User user);

    int deleteById(Integer userid);

    boolean updateUserById(User user);

    User queryById(Integer userid);

    List<User> queryAllUser();

    User checkLogin(String name, String pwd);

    User queryByName(String name);

    List<User> queryByKey(String key);


}
