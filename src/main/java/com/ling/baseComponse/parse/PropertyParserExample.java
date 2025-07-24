package com.ling.baseComponse.parse;

import org.apache.ibatis.parsing.PropertyParser;

import java.util.Properties;

public class PropertyParserExample {
    public static void main(String[] args) {
        // 创建属性集合
        Properties props = new Properties();
        props.setProperty("username", "root");
        props.setProperty("url", "jdbc:mysql://localhost:3306/mydb");

        // ${password:root} 使用默认值
        String configString = "数据库连接: ${url}, 用户名: ${username}, 密码: ${password:root}";

        // 使用 PropertyParser 解析
        String parsedString = PropertyParser.parse(configString, props);

        System.out.println("原始字符串: " + configString);
        System.out.println("解析后字符串: " + parsedString);
        // 输出: 数据库连接: jdbc:mysql://localhost:3306/mydb, 用户名: root, 密码: 123456
    }
}
