package com.ling;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.parsing.XNode;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Statement;

public class Readme {


    /**
     * Mybatis 单独使用  {@link com.ling.test01.Test01}
     */
    void read01() {}

    /**
     * mybatis dao 模式 {@link com.ling.test02.Test02}
     *
     * <ol>
     *     <li> 解析配置文件：{@link XMLConfigBuilder#parseConfiguration(XNode) } </li>
     *     <li> 解析映射器 {@link XMLConfigBuilder#mapperElement(XNode)} </li>
     * </ol>
     */
    void read02(){}

    /**
     * sql 命令解析
     *
     */
    void read03(){}

    /**
     * SqlSession
     *
     * <ol>
     *     <li> Executor 执行器 {@link org.apache.ibatis.session.ExecutorType} </li>
     *     <li> TransactionIsolationLevel 隔离级别 {@link org.apache.ibatis.session.TransactionIsolationLevel} </li>
     *     <li> 事务工厂获取事务(Jdbc 事务) {@link TransactionFactory#newTransaction(DataSource, TransactionIsolationLevel, boolean)}</li>
     *     <li> 创建执行器 Executor {@link org.apache.ibatis.session.Configuration#newExecutor(Transaction, ExecutorType)} </li>
     *
     *     <li> 创建 Mapper 接口代理对象 {@link org.apache.ibatis.binding.MapperRegistry#getMapper(Class, SqlSession)} </li>
     *     <li> MapperProxy 实现了 InvocationHandler,有 invoke 方法{@link org.apache.ibatis.binding.MapperProxy#invoke(Object, Method, Object[])} </li>
     *     <li> 获取 MapperMethod 对象，包含 sql、返回值等信息 {@link org.apache.ibatis.binding.MapperProxy#cachedInvoker(Method)}</li>
     *     <li> 解析获取 sql 语句    {@link org.apache.ibatis.binding.MapperMethod.SqlCommand#SqlCommand(Configuration, Class, Method)} </li>
     *     <li> 参数名称解析 @Param  {@link org.apache.ibatis.reflection.ParamNameResolver#ParamNameResolver(Configuration, Method)}</li>
     *     <li> 具体 sql 操作   {@link org.apache.ibatis.binding.MapperProxy.PlainMethodInvoker#invoke(Object, Method, Object[])}
     *              {@link org.apache.ibatis.binding.MapperMethod#execute(SqlSession, Object[])}
     *     </li>
     *     <li> 执行 sql 操作（select,update）：{@link org.apache.ibatis.session.defaults.DefaultSqlSession#selectOne(String, Object)}</li>
     *     <li> 查询数据库 {@link org.apache.ibatis.executor.BaseExecutor#queryFromDatabase(MappedStatement, Object, RowBounds, ResultHandler, CacheKey, BoundSql)}</li>
     *     <li> 创建 StatementHandler {@link Configuration#newStatementHandler(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)}</li>
     *     <li> 获取数据库连接 {@link org.apache.ibatis.executor.SimpleExecutor#prepareStatement(StatementHandler, Log)} </li>
     *     <li> 处理占位符 {@link org.apache.ibatis.executor.statement.PreparedStatementHandler#parameterize(Statement)}</li>
     *     <li> 调用数据库执行查询 {@link org.apache.ibatis.executor.statement.PreparedStatementHandler#query(Statement, ResultHandler)}</li>
     *     <li> 返回传处理  {@link org.apache.ibatis.executor.resultset.DefaultResultSetHandler#handleResultSets(Statement)} </li>
     *
     *
     *
     * </ol>
     */
    void read04(){}
}
