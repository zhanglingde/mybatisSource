package com.ling.baseComponse.parse;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;

public class GenericTokenParserTest {
    public static void main(String[] args) {
        // 定义 Token 处理器
        TokenHandler handler = new TokenHandler() {
            @Override
            public String handleToken(String content) {
                // 简单的变量替换逻辑
                switch (content) {
                    case "name":
                        return "张三";
                    case "age":
                        return "25";
                    case "city":
                        return "北京";
                    default:
                        return "未知";
                }
            }
        };

        // 创建解析器，解析 ${} 标记
        GenericTokenParser parser = new GenericTokenParser("${", "}", handler);

        // 测试文本
        String text = "我的名字是${name}，年龄${age}岁，来自${city}。";

        // 执行解析
        String result = parser.parse(text);

        System.out.println("原文本: " + text);
        System.out.println("解析后: " + result);
        // 输出: 我的名字是张三，年龄25岁，来自北京。
    }
}
