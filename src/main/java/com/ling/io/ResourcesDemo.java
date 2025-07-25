package com.ling.io;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

public class ResourcesDemo {
    public static void main(String[] args) {
        try {
            // MyBatis内部使用VFS来扫描mapper XML文件
            // 例如，在使用通配符配置mapper时：

            // 设置自定义VFS实现（如果需要）
            // VFS.addImplClass(CustomVFS.class);

            // MyBatis在解析配置时会使用VFS扫描资源
            String resource = "mybatis-config01.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);

            // 在构建SqlSessionFactory时，如果配置了mapper扫描，
            // MyBatis会使用VFS来查找和加载这些mapper文件
            SqlSessionFactory sqlSessionFactory =
                new SqlSessionFactoryBuilder().build(inputStream);

            System.out.println("SqlSessionFactory created successfully");

            // 当使用类似 <package name="com.example.mappers"/> 这样的配置时，
            // MyBatis会使用VFS来扫描指定包下的所有mapper接口

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
