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
package org.apache.ibatis.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * 保存了解析<select /> <update /> <delete /> <insert />标签内的SQL语句所生成的所有信息
 *
 * 内部定义了Builder构造器，使用了构建者模式构建对象
 *
 * @author Clinton Begin
 */
public final class MappedStatement {

    /**
     * XML 映射文件路径，例如：xxx/xxx/xxx.xml
     */
    private String resource;
    private Configuration configuration;
    /**
     * 唯一编号：`${namespace}.${id}`
     */
    private String id;
    /**
     * 这是一个给驱动的建议值，尝试让驱动程序每次批量返回的结果行数等于这个设置值
     * 默认值为未设置（unset）（依赖驱动）
     */
    private Integer fetchSize;
    /**
     * 这个设置是在抛出异常之前，驱动程序等待数据库返回请求结果的秒数
     * 默认值为未设置（unset）（依赖数据库驱动）
     */
    private Integer timeout;
    /**
     * Statement 的类型：STATEMENT PREPARED CALLABLE，默认 PREPARED
     * 分别对应：Statement PreparedStatement  CallableStatement
     */
    private StatementType statementType;
    private ResultSetType resultSetType;
    /**
     * SQL 相关信息
     */
    private SqlSource sqlSource;
    /**
     * 缓存对象
     */
    private Cache cache;
    private ParameterMap parameterMap;
    /**
     * ResultMap对象
     * 配置多个时需要加上 namespace 并以逗号分隔
     */
    private List<ResultMap> resultMaps;
    /**
     * 是否清空缓存
     */
    private boolean flushCacheRequired;
    /**
     * 是否使用缓存
     */
    private boolean useCache;
    /**
     * 这个设置仅针对嵌套结果 select 语句，默认值：false
     * 如果为 true，将会假设包含了嵌套结果集或是分组，当返回一个主结果行时，就不会产生对前面结果集的引用
     * 这就使得在获取嵌套结果集的时候不至于内存不够用
     */
    private boolean resultOrdered;
    /**
     * SQL 语句类型
     */
    private SqlCommandType sqlCommandType;
    /**
     * key 的生成器
     */
    private KeyGenerator keyGenerator;
    /**
     * key 的生成器的 Java 属性
     */
    private String[] keyProperties;
    /**
     * key 的生成器的 column 列名
     */
    private String[] keyColumns;
    /**
     * 是否有内嵌的 ResultMap
     */
    private boolean hasNestedResultMaps;
    /**
     * 数据库表示
     */
    private String databaseId;
    /**
     * 日志对象
     */
    private Log statementLog;
    /**
     * 语言驱动，默认为 XMLLanguageDriver
     */
    private LanguageDriver lang;
    /**
     * 这个设置仅适用于多结果集的情况
     * 它将列出语句执行后返回的结果集并赋予每个结果集一个名称，多个名称之间以逗号分隔
     */
    private String[] resultSets;

    MappedStatement() {
        // constructor disabled
    }

    public static class Builder {
        private MappedStatement mappedStatement = new MappedStatement();

        public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
            mappedStatement.configuration = configuration;
            mappedStatement.id = id;
            mappedStatement.sqlSource = sqlSource;
            mappedStatement.statementType = StatementType.PREPARED;
            mappedStatement.resultSetType = ResultSetType.DEFAULT;
            mappedStatement.parameterMap = new ParameterMap.Builder(configuration, "defaultParameterMap", null, new ArrayList<>()).build();
            mappedStatement.resultMaps = new ArrayList<>();
            mappedStatement.sqlCommandType = sqlCommandType;
            mappedStatement.keyGenerator = configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType) ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
            String logId = id;
            if (configuration.getLogPrefix() != null) {
                logId = configuration.getLogPrefix() + id;
            }
            mappedStatement.statementLog = LogFactory.getLog(logId);
            mappedStatement.lang = configuration.getDefaultScriptingLanguageInstance();
        }

        public Builder resource(String resource) {
            mappedStatement.resource = resource;
            return this;
        }

        public String id() {
            return mappedStatement.id;
        }

        public Builder parameterMap(ParameterMap parameterMap) {
            mappedStatement.parameterMap = parameterMap;
            return this;
        }

        public Builder resultMaps(List<ResultMap> resultMaps) {
            mappedStatement.resultMaps = resultMaps;
            for (ResultMap resultMap : resultMaps) {
                mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
            }
            return this;
        }

        public Builder fetchSize(Integer fetchSize) {
            mappedStatement.fetchSize = fetchSize;
            return this;
        }

        public Builder timeout(Integer timeout) {
            mappedStatement.timeout = timeout;
            return this;
        }

        public Builder statementType(StatementType statementType) {
            mappedStatement.statementType = statementType;
            return this;
        }

        public Builder resultSetType(ResultSetType resultSetType) {
            mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
            return this;
        }

        public Builder cache(Cache cache) {
            mappedStatement.cache = cache;
            return this;
        }

        public Builder flushCacheRequired(boolean flushCacheRequired) {
            mappedStatement.flushCacheRequired = flushCacheRequired;
            return this;
        }

        public Builder useCache(boolean useCache) {
            mappedStatement.useCache = useCache;
            return this;
        }

        public Builder resultOrdered(boolean resultOrdered) {
            mappedStatement.resultOrdered = resultOrdered;
            return this;
        }

        public Builder keyGenerator(KeyGenerator keyGenerator) {
            mappedStatement.keyGenerator = keyGenerator;
            return this;
        }

        public Builder keyProperty(String keyProperty) {
            mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
            return this;
        }

        public Builder keyColumn(String keyColumn) {
            mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
            return this;
        }

        public Builder databaseId(String databaseId) {
            mappedStatement.databaseId = databaseId;
            return this;
        }

        public Builder lang(LanguageDriver driver) {
            mappedStatement.lang = driver;
            return this;
        }

        public Builder resultSets(String resultSet) {
            mappedStatement.resultSets = delimitedStringToArray(resultSet);
            return this;
        }

        /**
         * Resul sets.
         *
         * @param resultSet the result set
         * @return the builder
         * @deprecated Use {@link #resultSets}
         */
        @Deprecated
        public Builder resulSets(String resultSet) {
            mappedStatement.resultSets = delimitedStringToArray(resultSet);
            return this;
        }

        public MappedStatement build() {
            assert mappedStatement.configuration != null;
            assert mappedStatement.id != null;
            assert mappedStatement.sqlSource != null;
            assert mappedStatement.lang != null;
            mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
            return mappedStatement;
        }
    }

    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    public SqlCommandType getSqlCommandType() {
        return sqlCommandType;
    }

    public String getResource() {
        return resource;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public String getId() {
        return id;
    }

    public boolean hasNestedResultMaps() {
        return hasNestedResultMaps;
    }

    public Integer getFetchSize() {
        return fetchSize;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public ResultSetType getResultSetType() {
        return resultSetType;
    }

    public SqlSource getSqlSource() {
        return sqlSource;
    }

    public ParameterMap getParameterMap() {
        return parameterMap;
    }

    public List<ResultMap> getResultMaps() {
        return resultMaps;
    }

    public Cache getCache() {
        return cache;
    }

    public boolean isFlushCacheRequired() {
        return flushCacheRequired;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public boolean isResultOrdered() {
        return resultOrdered;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public String[] getKeyProperties() {
        return keyProperties;
    }

    public String[] getKeyColumns() {
        return keyColumns;
    }

    public Log getStatementLog() {
        return statementLog;
    }

    public LanguageDriver getLang() {
        return lang;
    }

    public String[] getResultSets() {
        return resultSets;
    }

    /**
     * Gets the resul sets.
     *
     * @return the resul sets
     * @deprecated Use {@link #getResultSets()}
     */
    @Deprecated
    public String[] getResulSets() {
        return resultSets;
    }

    public BoundSql getBoundSql(Object parameterObject) {
        BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
        }

        // check for nested result maps in parameter mappings (issue #30)
        for (ParameterMapping pm : boundSql.getParameterMappings()) {
            String rmId = pm.getResultMapId();
            if (rmId != null) {
                ResultMap rm = configuration.getResultMap(rmId);
                if (rm != null) {
                    hasNestedResultMaps |= rm.hasNestedResultMaps();
                }
            }
        }

        return boundSql;
    }

    private static String[] delimitedStringToArray(String in) {
        if (in == null || in.trim().length() == 0) {
            return null;
        } else {
            return in.split(",");
        }
    }

}
