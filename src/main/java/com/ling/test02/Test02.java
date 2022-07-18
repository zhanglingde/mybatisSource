package com.ling.test02;

import com.ling.test02.entity.User;
import com.ling.test02.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

public class Test02 {

    @Test
    public void mybatisTest() {

        // 1. 读取该配置文件
        String config = "mybatis-config02.xml";
        InputStream is = Test02.class.getClassLoader().getResourceAsStream(config);

        // 2. 创建sql会话工厂
        SqlSessionFactory ssf = new SqlSessionFactoryBuilder().build(is);

        // 3. 创建sql会话
        SqlSession ss = ssf.openSession();

        // 通过这个方法反射得到接口的实例对象
        UserMapper userMapper = ss.getMapper(UserMapper.class);

        // 新增
        // User user = new User();
        // user.setUser_age(14);
        // user.setUser_name("zhangling");
        // user.setUser_pwd("123");
        // if (userMapper.addUser(user)) {
        //     System.out.println("添加成功");
        // }

        // User user = userMapper.queryById(1);
        // System.out.println("user = " + user);

        userMapper.deleteById(1);

        List<User> users = userMapper.queryAllUser();
        System.out.println("users = " + users);

        // User existUser = userMapper.queryByName("哈哈");
        // // 模糊查询
        // List<User> list = userMapper.queryByKey("zh");
        // System.out.println("list = " + list);

        ss.commit();
        ss.close();
    }



}
