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
package org.apache.ibatis.executor;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

    private static final Log log = LogFactory.getLog(BaseExecutor.class);

    // 实现事务提交、回滚、关闭等操作
    protected Transaction transaction;
    protected Executor wrapper;

    // 延迟加载队列（线程安全）
    protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
    /**
     * 本地缓存
     * 本地缓存机制（Local Cache）防止循环引用（circular references）和加速重复嵌套查询（一级缓存）
     */
    protected PerpetualCache localCache;
    /**
     * 本地输入参数缓存，和存储过程有关
     */
    protected PerpetualCache localOutputParameterCache;

    protected Configuration configuration;

    // 用来记录嵌套查询的层数，记录当前会话正在查询的数量
    protected int queryStack;
    private boolean closed;

    protected BaseExecutor(Configuration configuration, Transaction transaction) {
        this.transaction = transaction;
        this.deferredLoads = new ConcurrentLinkedQueue<>();
        this.localCache = new PerpetualCache("LocalCache");
        this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
        this.closed = false;
        this.configuration = configuration;
        this.wrapper = this;
    }

    @Override
    public Transaction getTransaction() {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        return transaction;
    }

    @Override
    public void close(boolean forceRollback) {
        try {
            try {
                rollback(forceRollback);
            } finally {
                if (transaction != null) {
                    transaction.close();
                }
            }
        } catch (SQLException e) {
            // Ignore. There's nothing that can be done at this point.
            log.warn("Unexpected exception on closing transaction.  Cause: " + e);
        } finally {
            transaction = null;
            deferredLoads = null;
            localCache = null;
            localOutputParameterCache = null;
            closed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * SqlSession update/insert/delete 会调用此方法
     * @param ms
     * @param parameter
     * @return
     * @throws SQLException
     */
    @Override
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        // 1. 先清局部缓存，再更新，如何更新交由子类，模板方法模式
        clearLocalCache();
        // 2. 执行写操作
        return doUpdate(ms, parameter);
    }

    @Override
    public List<BatchResult> flushStatements() throws SQLException {
        return flushStatements(false);
    }

    // 刷新语句，Batch 用
    public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        // 调用方法，其参数 idRollBack 表示执行 Executor 中缓存的 SQL 语句，false 表示执行，true 表示不执行
        return doFlushStatements(isRollBack);
    }

    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
        // 得到绑定 sql(动态 sql 会解析成 ?)
        BoundSql boundSql = ms.getBoundSql(parameter);
        // 创建缓存 key
        CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
        // 查询
        return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        // 1. 清空本地缓存，如果 queryStack 为零，并且要求清空本地缓存（配置了 flushCache = true）
        if (queryStack == 0 && ms.isFlushCacheRequired()) {
            //
            clearLocalCache();
        }
        List<E> list;
        try {
            // 增加查询层数，这样递归调用到上面的时候就不会再清局部缓存了
            queryStack++;
            // 2. 从一级缓存中，获取查询结果
            list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
            if (list != null) {
                // 3. 针对存储过程调用的处理，在一级缓存命中时，获取缓存中保存的输出类型参数，并设置到用户传入的实参对象中
                handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
            } else {
                // 3. 调用 doQuery 方法完成数据库查询，并得到映射后的结果对象
                list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
            }
        } finally {
            // 完成查询，堆栈出栈
            queryStack--;
        }
        if (queryStack == 0) {      // 4. 如果当前会话的所有查询执行完了
            // 4.1 延迟加载队列中所有元素
            // 在最外层的查询结束时，所有嵌套查询也已经完成，相关缓存项也已经完全记载，所以在此处触发DeferredLoad加载一级缓存中记录的嵌套查询的结果对象
            for (DeferredLoad deferredLoad : deferredLoads) {
                deferredLoad.load();
            }
            // issue #601
            // 4.2 清空延迟加载队列
            deferredLoads.clear();
            // 4.3 如果缓存级别是 LocalCacheScope.STATEMENT ，则进行清理
            if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
                // issue #482
                // 如果是 STATEMENT，清空一级缓存
                clearLocalCache();
            }
        }
        return list;
    }

    @Override
    public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        return doQueryCursor(ms, parameter, rowBounds, boundSql);
    }

    /**
     * 延迟加载，DefaultResultSetHandler.getNestedQueryMappingValue调用.属于嵌套查询，比较高级.
     * @param ms
     * @param resultObject
     * @param property
     * @param key
     * @param targetType
     */
    @Override
    public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
        if (deferredLoad.canLoad()) {
            // 一级缓存中已经记录了指定查询的结果对象,直接从缓存中加载对象,并设置到外层对象中
            deferredLoad.load();
        } else {
            // 将 DeferredLoad 对象添加到 deferredLoads 队列中,待整个外层查询结束后,再加载该结果对象
            deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
        }
    }

    /**
     * 创建缓存 key
     * @param ms
     * @param parameterObject
     * @param rowBounds
     * @param boundSql
     * @return
     */
    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
        if (closed) {
            throw new ExecutorException("Executor was closed.");
        }
        // 1. 创建 CacheKey 对象
        CacheKey cacheKey = new CacheKey();
        // 2. 设置 id、offset、limit、sql 到 CacheKey 对象中
        cacheKey.update(ms.getId());
        cacheKey.update(rowBounds.getOffset());
        cacheKey.update(rowBounds.getLimit());
        cacheKey.update(boundSql.getSql());
        // 3. 设置 ParameterMapping 数组的元素对应的每个 value 到 CacheKey 对象中
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
        // mimic DefaultParameterHandler logic
        // 获取用户传入的实参,并添加到 CacheKey 对象中(参考 DefaultParameterHandler)
        for (ParameterMapping parameterMapping : parameterMappings) {
            if (parameterMapping.getMode() != ParameterMode.OUT) {  // 该参数需要作为入参
                Object value;
                String propertyName = parameterMapping.getProperty();
                // 4. 获取属性值
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    // 从附加参数中获取
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject == null) {
                    // 入参对象为空则直接返回 null
                    value = null;
                } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                    // 入参有对应的类型处理器则直接返回该参数
                    value = parameterObject;
                } else {
                    // 从入参对象中获取该属性的值
                    MetaObject metaObject = configuration.newMetaObject(parameterObject);
                    value = metaObject.getValue(propertyName);
                }
                // 将实参添加到 cacheKey 对象中
                cacheKey.update(value);
            }
        }
        // 5. 设置 Environment.id 到 CacheKey 对象中
        if (configuration.getEnvironment() != null) {
            // issue #176
            cacheKey.update(configuration.getEnvironment().getId());
        }
        return cacheKey;
    }

    @Override
    public boolean isCached(MappedStatement ms, CacheKey key) {
        return localCache.getObject(key) != null;
    }

    @Override
    public void commit(boolean required) throws SQLException {
        if (closed) {
            throw new ExecutorException("Cannot commit, transaction is already closed");
        }
        // 清空一级缓存
        clearLocalCache();
        // 执行缓存的语句
        flushStatements();
        // 根据 required 参数决定是否提交事务
        if (required) {
            transaction.commit();
        }
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        if (!closed) {
            try {
                clearLocalCache();
                flushStatements(true);
            } finally {
                if (required) {
                    transaction.rollback();
                }
            }
        }
    }

    @Override
    public void clearLocalCache() {
        if (!closed) {
            localCache.clear();
            localOutputParameterCache.clear();
        }
    }

    protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

    protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

    protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException;

    protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
            throws SQLException;

    protected void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /**
     * Apply a transaction timeout.
     *
     * @param statement a current statement
     * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
     * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
     * @since 3.4.0
     */
    protected void applyTransactionTimeout(Statement statement) throws SQLException {
        StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
    }

    private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
        // 处理存储过程的 OUT 参数
        if (ms.getStatementType() == StatementType.CALLABLE) {
            final Object cachedParameter = localOutputParameterCache.getObject(key);
            if (cachedParameter != null && parameter != null) {
                final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
                final MetaObject metaParameter = configuration.newMetaObject(parameter);
                for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
                    if (parameterMapping.getMode() != ParameterMode.IN) {
                        final String parameterName = parameterMapping.getProperty();
                        final Object cachedValue = metaCachedParameter.getValue(parameterName);
                        metaParameter.setValue(parameterName, cachedValue);
                    }
                }
            }
        }
    }

    /**
     * 从数据库查
     */
    private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
        List<E> list;
        // 1. 先向缓存中放入占位符 ? ? ?（因为正在执行的查询不允许提前加载需要延迟加载的属性，可见 DeferredLoad#canLoad() 方法）
        localCache.putObject(key, EXECUTION_PLACEHOLDER);
        try {
            // 2. 完成数据库查询操作，并返回结果对象
            list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
        } finally {
            // 3. 从缓存中，移除占位对象
            localCache.removeObject(key);
        }
        // 4. 将真正的结果对象添加到一级缓存
        localCache.putObject(key, list);
        // 5. 如果是存储过程调用，OUT 参数也加入缓存
        if (ms.getStatementType() == StatementType.CALLABLE) {
            localOutputParameterCache.putObject(key, parameter);
        }
        return list;
    }

    protected Connection getConnection(Log statementLog) throws SQLException {
        Connection connection = transaction.getConnection();
        if (statementLog.isDebugEnabled()) {
            // 如果需要打印 Connection 的日志，返回一个 ConnectionLogger (代理模式, AOP思想)
            return ConnectionLogger.newInstance(connection, statementLog, queryStack);
        } else {
            return connection;
        }
    }

    @Override
    public void setExecutorWrapper(Executor wrapper) {
        this.wrapper = wrapper;
    }

    private static class DeferredLoad {
        // 外层对象对应的 MetaObject 对象
        private final MetaObject resultObject;
        // 延迟加载的属性名称
        private final String property;
        // 延迟加载的属性类型
        private final Class<?> targetType;
        // 延迟加载的结果对象在一级缓存中相应的CacheKey对象
        private final CacheKey key;
        private final PerpetualCache localCache;
        private final ObjectFactory objectFactory;
        // ResultExtractor负责结果对象的类型转换
        private final ResultExtractor resultExtractor;

        // issue #
        // 延迟加载
        public DeferredLoad(MetaObject resultObject,
                            String property,
                            CacheKey key,
                            PerpetualCache localCache,
                            Configuration configuration,
                            Class<?> targetType) {
            this.resultObject = resultObject;
            this.property = property;
            this.key = key;
            this.localCache = localCache;
            this.objectFactory = configuration.getObjectFactory();
            this.resultExtractor = new ResultExtractor(configuration, objectFactory);
            this.targetType = targetType;
        }

        public boolean canLoad() {
            // 缓存中找到，且不为占位符，代表可以加载
            return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
        }

        public void load() {
            @SuppressWarnings("unchecked")
            // we suppose we get back a List
            // 从缓存中查询指定的结果对象
            List<Object> list = (List<Object>) localCache.getObject(key);
            // 将缓存的结果对象转换成指定类型
            Object value = resultExtractor.extractObjectFromList(list, targetType);
            resultObject.setValue(property, value);
        }

    }

}
