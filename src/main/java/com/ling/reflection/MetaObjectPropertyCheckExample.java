package com.ling.reflection;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.util.Arrays;

/**
 * 3. 动态属性检查案例
 */
public class MetaObjectPropertyCheckExample {
    public static void main(String[] args) {
        User user = new User();
        MetaObject metaObject = SystemMetaObject.forObject(user);

        // 检查属性是否存在
        System.out.println("是否有 id 属性: " + metaObject.hasGetter("id"));
        System.out.println("是否有 name 属性: " + metaObject.hasGetter("name"));
        System.out.println("是否有 age 属性: " + metaObject.hasGetter("age"));
        System.out.println("是否有 notExist 属性: " + metaObject.hasGetter("notExist"));

        // 检查 setter
        System.out.println("是否有 id 的 setter: " + metaObject.hasSetter("id"));
        System.out.println("是否有 name 的 setter: " + metaObject.hasSetter("name"));

        // 获取属性类型
        System.out.println("id 属性类型: " + metaObject.getGetterType("id"));
        System.out.println("name 属性类型: " + metaObject.getGetterType("name"));
        System.out.println("address 属性类型: " + metaObject.getGetterType("address"));

        // 获取所有 getter 和 setter 名称
        System.out.println("所有 getter: " + Arrays.toString(metaObject.getGetterNames()));
        System.out.println("所有 setter: " + Arrays.toString(metaObject.getSetterNames()));
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

