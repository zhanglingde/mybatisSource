package com.ling.test02;

import com.github.pagehelper.PageHelper;
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

        // 3. 获取数据库的会话，创建出数据库连接的会话对象（事务工厂、事务对象，执行器，如果有插件会进行插件的解析）（还未开启 session）
        SqlSession sqlSession = ssf.openSession();

        // 通过这个方法反射得到接口的实例对象（mapperRegistry.knowMapper）
        UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

        // 新增
        // User user = new User();
        // user.setUser_age(14);
        // user.setUser_name("zhangling");
        // user.setUser_pwd("123");
        // if (userMapper.addUser(user)) {
        //     System.out.println("添加成功");
        // }

        // User user = userMapper.selectById(2);
        // System.out.println("user = " + user);

        PageHelper.startPage(1, 3);
        List<User> list = userMapper.queryAllUser();
        System.out.println("list = " + list);

        // userMapper.deleteById(1);
        //
        // List<User> users = userMapper.queryAllUser();
        // System.out.println("users = " + users);

        // User existUser = userMapper.queryByName("哈哈");
        // // 模糊查询
        // List<User> list = userMapper.queryByKey("zh");
        // System.out.println("list = " + list);

        sqlSession.commit();
        sqlSession.close();
    }



}
