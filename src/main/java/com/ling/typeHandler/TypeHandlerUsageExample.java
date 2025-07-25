package com.ling.typeHandler;

import org.apache.ibatis.type.*;

import java.math.BigDecimal;
import java.sql.*;

/**
 * TypeHandler 使用示例
 */
public class TypeHandlerUsageExample {
    public static void main(String[] args) throws SQLException {
        // 模拟数据库连接和语句对象
        simulateTypeHandlerUsage();
    }

    public static void simulateTypeHandlerUsage() throws SQLException {
        // 1. 测试 TypeHandler
        StringTypeHandler stringTypeHandler = new StringTypeHandler();
        BigDecimalTypeHandler bigDecimalTypeHandler = new BigDecimalTypeHandler();
        IntegerTypeHandler integerTypeHandler = new IntegerTypeHandler();

        // 2. 模拟 PreparedStatement 参数设置
        System.out.println("=== 模拟设置PreparedStatement参数 ===");
        MockPreparedStatement mockPs = new MockPreparedStatement();

        // 使用 TypeHandler 设置参数到 PreparedStatement 中
        stringTypeHandler.setNonNullParameter(mockPs, 1, "Hello World", JdbcType.VARCHAR);
        System.out.println("设置String参数: " + mockPs.getParameter(1));

        BigDecimal price = new BigDecimal("99.99");
        bigDecimalTypeHandler.setNonNullParameter(mockPs, 2, price, JdbcType.DECIMAL);
        System.out.println("设置BigDecimal参数: " + mockPs.getParameter(2));

        integerTypeHandler.setNonNullParameter(mockPs, 3, 42, JdbcType.INTEGER);
        System.out.println("设置Integer参数: " + mockPs.getParameter(3));

        // 3. 模拟ResultSet结果获取
        System.out.println("\n=== 模拟从ResultSet获取结果 ===");
        MockResultSet mockRs = new MockResultSet();
        mockRs.setString("name", "Product Name");
        mockRs.setBigDecimal("price", new BigDecimal("199.99"));
        mockRs.setInt("quantity", 100);

        // TypeHandler 获取到的参数，直接就是对应的类型的参数
        String name = stringTypeHandler.getNullableResult(mockRs, "name");
        System.out.println("获取String结果: " + name);

        BigDecimal resultPrice = bigDecimalTypeHandler.getNullableResult(mockRs, "price");
        System.out.println("获取BigDecimal结果: " + resultPrice);

        Integer quantity = integerTypeHandler.getNullableResult(mockRs, "quantity");
        System.out.println("获取Integer结果: " + quantity);

        // 4. 演示TypeHandlerRegistry的使用(初始化时就注册了内置类型处理器)
        System.out.println("\n=== 演示TypeHandlerRegistry使用 ===");
        TypeHandlerRegistry registry = new TypeHandlerRegistry();

        // 从注册表获取类型处理器
        TypeHandler<String> stringHandler = registry.getTypeHandler(String.class);
        TypeHandler<BigDecimal> bdHandler = registry.getTypeHandler(BigDecimal.class);
        TypeHandler<Integer> intHandler = registry.getTypeHandler(Integer.class);

        System.out.println("从注册表获取String类型处理器: " + stringHandler.getClass().getSimpleName());
        System.out.println("从注册表获取BigDecimal类型处理器: " + bdHandler.getClass().getSimpleName());
        System.out.println("从注册表获取Integer类型处理器: " + intHandler.getClass().getSimpleName());

        // 5. 演示类型处理器处理null值
        System.out.println("\n=== 演示null值处理 ===");
        MockResultSet nullRs = new MockResultSet();
        nullRs.setNull("name", Types.VARCHAR);
        nullRs.setNull("", Types.DECIMAL);

        String nullName = stringTypeHandler.getNullableResult(nullRs, "name");
        BigDecimal nullPrice = bigDecimalTypeHandler.getNullableResult(nullRs, "price");

        System.out.println("获取null String结果: " + nullName);
        System.out.println("获取null BigDecimal结果: " + nullPrice);
    }
}

