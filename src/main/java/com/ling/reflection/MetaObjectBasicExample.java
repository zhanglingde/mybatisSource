package com.ling.reflection;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * 1. 基础使用案例
 */
public class MetaObjectBasicExample {
    public static void main(String[] args) {
        User user = new User();
        user.setId(1);
        user.setName("张三");
        user.setAge(25);

        Address address = new Address();
        address.setCity("北京");
        address.setStreet("长安街1号");
        user.setAddress(address);

        // 创建 MetaObject
        MetaObject metaObject = SystemMetaObject.forObject(user);

        // 获取属性值
        System.out.println("用户ID: " + metaObject.getValue("id"));
        System.out.println("用户名: " + metaObject.getValue("name"));
        System.out.println("用户年龄: " + metaObject.getValue("age"));

        // 获取嵌套属性值
        System.out.println("城市: " + metaObject.getValue("address.city"));
        System.out.println("街道: " + metaObject.getValue("address.street"));

        // 设置属性值
        metaObject.setValue("name", "李四");
        metaObject.setValue("address.city", "上海");

        System.out.println("修改后的用户名: " + user.getName());
        System.out.println("修改后的城市: " + user.getAddress().getCity());
    }

    static class User {
        private Integer id;
        private String name;
        private Integer age;
        private Address address;

        // Getters and Setters
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }

        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    static class Address {
        private String city;
        private String street;

        // Getters and Setters
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }
}

