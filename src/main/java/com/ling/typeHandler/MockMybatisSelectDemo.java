package com.ling.typeHandler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 模拟 Mybatis 查询
 */
public class MockMybatisSelectDemo {
    public static void main(String[] args) throws SQLException {
        System.out.println("=== MyBatis中TypeHandler的实际使用场景 ===");

        // 1. 模拟MyBatis参数处理过程
        System.out.println("\n1. 参数处理过程（PreparedStatement参数设置）:");
        processParameters();

        // 2. 模拟MyBatis结果映射过程
        System.out.println("\n2. 结果映射过程（ResultSet结果获取）:");
        processResults();

        // 3. 演示复杂对象的处理
        System.out.println("\n3. 复杂对象处理:");
        processComplexObject();
    }

    private static void processParameters() throws SQLException {
        // 模拟MyBatis处理 INSERT 语句参数
        String sql = "INSERT INTO users(name, age, salary) VALUES (?, ?, ?)";
        MockPreparedStatement ps = new MockPreparedStatement();

        // 模拟用户对象
        String userName = "张三";
        Integer userAge = 25;
        BigDecimal userSalary = new BigDecimal("8000.50");

        // MyBatis 通过 TypeHandlerRegistry 获取对应处理器
        TypeHandlerRegistry registry = new TypeHandlerRegistry();

        // 处理String参数
        TypeHandler<String> stringHandler = registry.getTypeHandler(String.class);
        stringHandler.setParameter(ps, 1, userName, JdbcType.VARCHAR);
        System.out.println("  设置第1个参数(String): " + ps.getParameter(1));

        // 处理Integer参数
        TypeHandler<Integer> integerHandler = registry.getTypeHandler(Integer.class);
        integerHandler.setParameter(ps, 2, userAge, JdbcType.INTEGER);
        System.out.println("  设置第2个参数(Integer): " + ps.getParameter(2));

        // 处理BigDecimal参数
        TypeHandler<BigDecimal> bigDecimalHandler = registry.getTypeHandler(BigDecimal.class);
        bigDecimalHandler.setParameter(ps, 3, userSalary, JdbcType.DECIMAL);
        System.out.println("  设置第3个参数(BigDecimal): " + ps.getParameter(3));
    }

    private static void processResults() throws SQLException {
        // 模拟MyBatis处理查询结果
        MockResultSet rs = new MockResultSet();
        rs.setString("name", "李四");
        rs.setInt("age", 30);
        rs.setBigDecimal("salary", new BigDecimal("12000.00"));

        // MyBatis通过TypeHandlerRegistry获取对应处理器
        TypeHandlerRegistry registry = new TypeHandlerRegistry();

        // 映射String字段
        TypeHandler<String> stringHandler = registry.getTypeHandler(String.class);
        String name = stringHandler.getResult(rs, "name");
        System.out.println("  获取name字段(String): " + name);

        // 映射Integer字段
        TypeHandler<Integer> integerHandler = registry.getTypeHandler(Integer.class);
        Integer age = integerHandler.getResult(rs, "age");
        System.out.println("  获取age字段(Integer): " + age);

        // 映射BigDecimal字段
        TypeHandler<BigDecimal> bigDecimalHandler = registry.getTypeHandler(BigDecimal.class);
        BigDecimal salary = bigDecimalHandler.getResult(rs, "salary");
        System.out.println("  获取salary字段(BigDecimal): " + salary);
    }

    private static void processComplexObject() throws SQLException {
        System.out.println("  处理用户对象:");

        // 模拟数据库查询结果
        MockResultSet rs = new MockResultSet();
        rs.setString("user_name", "王五");
        rs.setInt("user_age", 28);
        rs.setBigDecimal("user_salary", new BigDecimal("9500.75"));
        rs.setString("department", "技术部");

        // 模拟MyBatis的结果映射过程
        TypeHandlerRegistry registry = new TypeHandlerRegistry();

        // 逐个字段映射
        TypeHandler<String> stringHandler = registry.getTypeHandler(String.class);
        TypeHandler<Integer> integerHandler = registry.getTypeHandler(Integer.class);
        TypeHandler<BigDecimal> decimalHandler = registry.getTypeHandler(BigDecimal.class);

        String userName = stringHandler.getResult(rs, "user_name");
        Integer userAge = integerHandler.getResult(rs, "user_age");
        BigDecimal userSalary = decimalHandler.getResult(rs, "user_salary");
        String department = stringHandler.getResult(rs, "department");

        // 构建用户对象
        Map<String, Object> user = new HashMap<>();
        user.put("name", userName);
        user.put("age", userAge);
        user.put("salary", userSalary);
        user.put("department", department);

        System.out.println("  构建的用户对象: " + user);
    }
}
