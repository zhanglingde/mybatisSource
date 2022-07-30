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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 默认参数处理器
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

    /**
     * TypeHandlerRegistry 对象，管理 mybatis 中的全部 TypeHandler 对象
     */
    private final TypeHandlerRegistry typeHandlerRegistry;

    // 记录 sql 节点相应的配置信息
    private final MappedStatement mappedStatement;
    // 用户传入的实参对象
    private final Object parameterObject;
    // 对应的 BoundSql 对象，需要设置参数的PreparedStatement对象，就是根据该BoundSql中记录的SQL语句创建的，BoundSql中也记录了对应参数的名称和相关属性
    private final BoundSql boundSql;
    private final Configuration configuration;

    public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        this.mappedStatement = mappedStatement;
        this.configuration = mappedStatement.getConfiguration();
        this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
        this.parameterObject = parameterObject;
        this.boundSql = boundSql;
    }

    @Override
    public Object getParameterObject() {
        return parameterObject;
    }

    /**
     * 设置参数
     *
     * @param ps
     */
    @Override
    public void setParameters(PreparedStatement ps) {
        ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
        // 取出 sql 中映射的参数列表
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                // 过滤掉存储过程中的输出参数
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    // 记录绑定的实参
                    Object value;
                    // 获取参数名称
                    String propertyName = parameterMapping.getProperty();
                    if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
                        // 若有额外的参数，设为额外的参数
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        // 若参数为 null，直接设为  null
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        // 若参数有相应的 TypeHandler，直接设 object
                        value = parameterObject;
                    } else {
                        // 除此以外，MetaObject.getValue 反射取得值设进去
                        MetaObject metaObject = configuration.newMetaObject(parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    TypeHandler typeHandler = parameterMapping.getTypeHandler();
                    JdbcType jdbcType = parameterMapping.getJdbcType();
                    if (value == null && jdbcType == null) {
                        // 不同类型的 set 方法不同，所以委派给子类的 setParameter 方法
                        jdbcType = configuration.getJdbcTypeForNull();
                    }
                    try {
                        // 调用 PreparedStatement.set* 方法为 SQL 语句绑定相应的实参
                        typeHandler.setParameter(ps, i + 1, value, jdbcType);
                    } catch (TypeException | SQLException e) {
                        throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
                    }
                }
            }
        }
    }

}
