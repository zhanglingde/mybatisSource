package com.ling.reflection;

/**
 * 2. 集合操作案例
 */

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.util.*;

public class MetaObjectCollectionExample {
    public static void main(String[] args) {
        // 创建包含集合的用户对象
        User user = new User();
        user.setName("张三");

        List<String> hobbies = new ArrayList<>(8);
        hobbies.add("读书");
        hobbies.add("游泳");
        user.setHobbies(hobbies);

        Map<String, String> contacts = new HashMap<>();
        contacts.put("email", "zhangsan@example.com");
        contacts.put("phone", "13800138000");
        user.setContacts(contacts);

        // 创建 MetaObject
        MetaObject metaObject = SystemMetaObject.forObject(user);

        // 操作 List 集合
        // System.out.println("爱好数量: " + metaObject.getValue("hobbies.size"));  // 为什么这个不能嵌套获取？
        System.out.println("爱好数量: " + ((List<?>) metaObject.getValue("hobbies")).size());
        System.out.println("第一个爱好: " + metaObject.getValue("hobbies[0]"));

        // 添加新的爱好
        // metaObject.setValue("hobbies[2]", "跑步");    // 会报错
        List<String> hobbies2 = (List<String>) metaObject.getValue("hobbies");
        hobbies2.add("跑步");
        System.out.println("添加后的爱好: " + user.getHobbies());

        // 操作 Map 集合
        System.out.println("邮箱: " + metaObject.getValue("contacts.email"));
        System.out.println("电话: " + metaObject.getValue("contacts.phone"));

        // 添加新的联系方式
        metaObject.setValue("contacts.wechat", "zhangsan_wechat");
        System.out.println("微信: " + user.getContacts().get("wechat"));
    }

    static class User {
        private String name;
        private List<String> hobbies;
        private Map<String, String> contacts;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getHobbies() {
            return hobbies;
        }

        public void setHobbies(List<String> hobbies) {
            this.hobbies = hobbies;
        }

        public Map<String, String> getContacts() {
            return contacts;
        }

        public void setContacts(Map<String, String> contacts) {
            this.contacts = contacts;
        }
    }
}

