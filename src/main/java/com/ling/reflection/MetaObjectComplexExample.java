package com.ling.reflection;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import java.util.*;

/**
 * 4. 复杂嵌套对象案例
 */
public class MetaObjectComplexExample {
    public static void main(String[] args) {
        // 创建复杂的嵌套对象
        Company company = new Company();
        company.setName("ABC公司");

        Department dept = new Department();
        dept.setName("技术部");

        List<Employee> employees = new ArrayList<>();

        Employee emp1 = new Employee();
        emp1.setName("张三");
        emp1.setSalary(10000.0);

        Address addr1 = new Address();
        addr1.setCity("北京");
        addr1.setStreet("中关村大街1号");
        emp1.setAddress(addr1);

        employees.add(emp1);

        Employee emp2 = new Employee();
        emp2.setName("李四");
        emp2.setSalary(12000.0);

        Address addr2 = new Address();
        addr2.setCity("上海");
        addr2.setStreet("陆家嘴金融街");
        emp2.setAddress(addr2);

        employees.add(emp2);

        dept.setEmployees(employees);
        company.setDepartment(dept);

        // 创建 MetaObject
        MetaObject metaObject = SystemMetaObject.forObject(company);

        // 访问深层嵌套属性
        System.out.println("公司名称: " + metaObject.getValue("name"));
        System.out.println("部门名称: " + metaObject.getValue("department.name"));
        System.out.println("第一个员工姓名: " + metaObject.getValue("department.employees[0].name"));
        System.out.println("第一个员工城市: " + metaObject.getValue("department.employees[0].address.city"));
        System.out.println("第二个员工薪资: " + metaObject.getValue("department.employees[1].salary"));

        // 修改深层嵌套属性
        metaObject.setValue("department.employees[0].name", "张三丰");
        metaObject.setValue("department.employees[0].address.city", "深圳");

        System.out.println("修改后的第一个员工姓名: " + emp1.getName());
        System.out.println("修改后的第一个员工城市: " + emp1.getAddress().getCity());
    }

    static class Company {
        private String name;
        private Department department;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Department getDepartment() { return department; }
        public void setDepartment(Department department) { this.department = department; }
    }

    static class Department {
        private String name;
        private List<Employee> employees;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<Employee> getEmployees() { return employees; }
        public void setEmployees(List<Employee> employees) { this.employees = employees; }
    }

    static class Employee {
        private String name;
        private Double salary;
        private Address address;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getSalary() { return salary; }
        public void setSalary(Double salary) { this.salary = salary; }

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

