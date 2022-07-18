package com.ling.test01;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

public class Test01 {

    @Test
    public void addUser() {
        // 配置文件名称
        String config = "mybatis-config01.xml";
        // 读取该配置文件
        InputStream is = Test01.class.getClassLoader().getResourceAsStream(config);

        // 2.创建sql会话工厂
        SqlSessionFactory ssf = new SqlSessionFactoryBuilder().build(is);
        // 3.创建sql会话
        SqlSession ss = ssf.openSession();
        // 4.通过sql会话来执行增删改查
        User user = new User();
        user.setUser_name("ling");
        user.setUser_pwd("abc");
        user.setUser_age(60);

        int i = ss.insert("addUser", user);
        if (i > 0) {
            System.out.println("插入成功");
        } else {
            System.out.println("插入失败");
        }
        // 如果是增删改语句 需要commit方法
        ss.commit();
        // 使用完成后手动关闭session
        ss.close();
    }
}
