/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 预处理语句处理器（PREPARED）
 *
 * @author Clinton Begin
 */
public class PreparedStatementHandler extends BaseStatementHandler {

    public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public int update(Statement statement) throws SQLException {
        // 调用 PreparedStatement.execute 和 PreparedStatement.getUpdateCount
        PreparedStatement ps = (PreparedStatement) statement;
        ps.execute();
        // 获得更新数量
        int rows = ps.getUpdateCount();
        Object parameterObject = boundSql.getParameterObject();
        /*
         * 获得 KeyGenerator 对象
         * 1. 配置了 <selectKey /> 则会生成 SelectKeyGenerator 对象
         * 2. 配置了 useGeneratedKeys="true" 则会生成 Jdbc3KeyGenerator 对象
         * 否则为 NoKeyGenerator 对象
         */
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        // 执行 keyGenerator 的后置处理逻辑，也就是对我们配置的自增键进行赋值
        keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
        return rows;
    }

    @Override
    public void batch(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        // 添加到批处理
        ps.addBatch();
    }

    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        // 调用数据库执行查询
        ps.execute();
        // 返回值设置
        return resultSetHandler.handleResultSets(ps);
    }

    @Override
    public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        ps.execute();
        return resultSetHandler.handleCursorResultSets(ps);
    }

    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        // 获取绑定的 sql 语句
        String sql = boundSql.getSql();
        /*
         * 获得 KeyGenerator 对象
         * 1. 配置了 <selectKey /> 则会生成 SelectKeyGenerator 对象
         * 2. 配置了 useGeneratedKeys="true" 则会生成 Jdbc3KeyGenerator 对象
         * 否则为 NoKeyGenerator 对象
         */
        if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
            // 1. 获得 keyColumn 配置
            String[] keyColumnNames = mappedStatement.getKeyColumns();
            if (keyColumnNames == null) {
                // 2. 创建 PreparedStatement 对象，并返回自增键，并可通过 getGeneratedKeys() 方法获取（返回数据库生成的主键）
                return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            } else {
                // 在 insert 语句执行完成之后，会将 keyColumnNames 指定的列返回
                // 3. 创建 PreparedStatement 对象，并返回我们配置的 column 列名自增键，并可通过 getGeneratedKeys() 方法获取
                return connection.prepareStatement(sql, keyColumnNames);
            }
        } else if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
            // 创建普通的 PreparedStatement 对象
            return connection.prepareStatement(sql);
        } else {
            // 设置结果集是否可以滚动以及其游标是否可以上下移动，设置结果集是否可更新
            return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
        }
    }

    @Override
    public void parameterize(Statement statement) throws SQLException {
        // 通过 DefaultParameterHandler 调用 ParameterHandler.setParameters 处理占位符
        parameterHandler.setParameters((PreparedStatement) statement);
    }

}
