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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.util.MapUtil;

/**
 * 只有后置处理的实现，获取返回的自增键，设置到入参的属性中，配置了useGeneratedKeys="true"则会创建该对象
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

    private static final String SECOND_GENERIC_PARAM_NAME = ParamNameResolver.GENERIC_NAME_PREFIX + "2";

    /**
     * A shared instance.
     *
     * @since 3.4.3
     */
    public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

    private static final String MSG_TOO_MANY_KEYS = "Too many keys are generated. There are only %d target objects. "
        + "You either specified a wrong 'keyProperty' or encountered a driver bug like #1523.";

    @Override
    public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        // do nothing
    }

    @Override
    public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        // 批处理多个自增键
        processBatch(ms, stmt, parameter);
    }

    public void processBatch(MappedStatement ms, Statement stmt, Object parameter) {
        // 获取 keyProperty 配置
        final String[] keyProperties = ms.getKeyProperties();
        if (keyProperties == null || keyProperties.length == 0) {
            return;
        }
        // 获取 Statement 执行后自增键对应的 ResultSet 对象
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            // 获取 ResultSet 的 ResultSetMetaData 元数据对象
            final ResultSetMetaData rsmd = rs.getMetaData();
            final Configuration configuration = ms.getConfiguration();
            if (rsmd.getColumnCount() < keyProperties.length) { // 自增键与 keyProperty 数量不一致则跳过
                // Error?
            } else {
                assignKeys(configuration, rs, rsmd, keyProperties, parameter);
            }
        } catch (Exception e) {
            throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
        }
    }

    /**
     * 关于 ParamMap，可以看到 {@link org.apache.ibatis.reflection.ParamNameResolver#getNamedParams} 这个方法
     * 根据入参获取获取参数名称与参数值的映射关系
     * 如果为多个入参或者一个入参并且添加了 @Param 注解，则会返回 ParamMap 对象，key为参数名称，value为参数值
     *
     * 需要使用到获取自增键时，我们一般入参都是一个实体类，则进入的是下面第3种情况
     */
    @SuppressWarnings("unchecked")
    private void assignKeys(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd, String[] keyProperties,
                            Object parameter) throws SQLException {
        if (parameter instanceof ParamMap || parameter instanceof StrictMap) {
            // Multi-param or single param with @Param
            assignKeysToParamMap(configuration, rs, rsmd, keyProperties, (Map<String, ?>) parameter);
        } else if (parameter instanceof ArrayList && !((ArrayList<?>) parameter).isEmpty()
            && ((ArrayList<?>) parameter).get(0) instanceof ParamMap) {
            // Multi-param or single param with @Param in batch operation
            assignKeysToParamMapList(configuration, rs, rsmd, keyProperties, (ArrayList<ParamMap<?>>) parameter);
        } else {
            // Single param without @Param
            assignKeysToParam(configuration, rs, rsmd, keyProperties, parameter);
        }
    }

    private void assignKeysToParam(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
                                   String[] keyProperties, Object parameter) throws SQLException {
        // 将入参对象 parameter 转换成集合，因为批处理时可能传入多个入参对象
        Collection<?> params = collectionize(parameter);
        if (params.isEmpty()) {
            return;
        }
        List<KeyAssigner> assignerList = new ArrayList<>();
        for (int i = 0; i < keyProperties.length; i++) {
            // 每一个 keyProperty 都创建一个 KeyAssigner 对象，设置 column 的位置
            assignerList.add(new KeyAssigner(configuration, rsmd, i + 1, null, keyProperties[i]));
        }
        Iterator<?> iterator = params.iterator();
        while (rs.next()) {
            if (!iterator.hasNext()) {
                throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, params.size()));
            }
            Object param = iterator.next();
            // 往每个入参对象中设置配置的 keyProperty 为对应的自增键
            assignerList.forEach(x -> x.assign(rs, param));
        }
    }

    private void assignKeysToParamMapList(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
                                          String[] keyProperties, ArrayList<ParamMap<?>> paramMapList) throws SQLException {
        Iterator<ParamMap<?>> iterator = paramMapList.iterator();
        List<KeyAssigner> assignerList = new ArrayList<>();
        long counter = 0;
        while (rs.next()) {
            if (!iterator.hasNext()) {
                throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
            }
            ParamMap<?> paramMap = iterator.next();
            if (assignerList.isEmpty()) {
                for (int i = 0; i < keyProperties.length; i++) {
                    assignerList
                        .add(getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i], keyProperties, false)
                            .getValue());
                }
            }
            assignerList.forEach(x -> x.assign(rs, paramMap));
            counter++;
        }
    }

    private void assignKeysToParamMap(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
                                      String[] keyProperties, Map<String, ?> paramMap) throws SQLException {
        if (paramMap.isEmpty()) {
            return;
        }
        Map<String, Entry<Iterator<?>, List<KeyAssigner>>> assignerMap = new HashMap<>();
        for (int i = 0; i < keyProperties.length; i++) {
            Entry<String, KeyAssigner> entry = getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i],
                keyProperties, true);
            Entry<Iterator<?>, List<KeyAssigner>> iteratorPair = MapUtil.computeIfAbsent(assignerMap, entry.getKey(),
                k -> MapUtil.entry(collectionize(paramMap.get(k)).iterator(), new ArrayList<>()));
            iteratorPair.getValue().add(entry.getValue());
        }
        long counter = 0;
        while (rs.next()) {
            for (Entry<Iterator<?>, List<KeyAssigner>> pair : assignerMap.values()) {
                if (!pair.getKey().hasNext()) {
                    throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
                }
                Object param = pair.getKey().next();
                pair.getValue().forEach(x -> x.assign(rs, param));
            }
            counter++;
        }
    }

    private Entry<String, KeyAssigner> getAssignerForParamMap(Configuration config, ResultSetMetaData rsmd,
                                                              int columnPosition, Map<String, ?> paramMap, String keyProperty, String[] keyProperties, boolean omitParamName) {
        Set<String> keySet = paramMap.keySet();
        // A caveat : if the only parameter has {@code @Param("param2")} on it,
        // it must be referenced with param name e.g. 'param2.x'.
        boolean singleParam = !keySet.contains(SECOND_GENERIC_PARAM_NAME);
        int firstDot = keyProperty.indexOf('.');
        if (firstDot == -1) {
            if (singleParam) {
                return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
            }
            throw new ExecutorException("Could not determine which parameter to assign generated keys to. "
                + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
                + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
                + keySet);
        }
        String paramName = keyProperty.substring(0, firstDot);
        if (keySet.contains(paramName)) {
            String argParamName = omitParamName ? null : paramName;
            String argKeyProperty = keyProperty.substring(firstDot + 1);
            return MapUtil.entry(paramName, new KeyAssigner(config, rsmd, columnPosition, argParamName, argKeyProperty));
        } else if (singleParam) {
            return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
        } else {
            throw new ExecutorException("Could not find parameter '" + paramName + "'. "
                + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
                + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
                + keySet);
        }
    }

    private Entry<String, KeyAssigner> getAssignerForSingleParam(Configuration config, ResultSetMetaData rsmd,
                                                                 int columnPosition, Map<String, ?> paramMap, String keyProperty, boolean omitParamName) {
        // Assume 'keyProperty' to be a property of the single param.
        String singleParamName = nameOfSingleParam(paramMap);
        String argParamName = omitParamName ? null : singleParamName;
        return MapUtil.entry(singleParamName, new KeyAssigner(config, rsmd, columnPosition, argParamName, keyProperty));
    }

    private static String nameOfSingleParam(Map<String, ?> paramMap) {
        // There is virtually one parameter, so any key works.
        return paramMap.keySet().iterator().next();
    }

    private static Collection<?> collectionize(Object param) {
        if (param instanceof Collection) {
            return (Collection<?>) param;
        } else if (param instanceof Object[]) {
            return Arrays.asList((Object[]) param);
        } else {
            return Arrays.asList(param);
        }
    }

    private class KeyAssigner {
        private final Configuration configuration;
        /**
         * Statement 执行后返回的 元数据对象
         */
        private final ResultSetMetaData rsmd;
        /**
         * 类型处理器注册中心
         */
        private final TypeHandlerRegistry typeHandlerRegistry;
        /**
         * 属性对应列所在的位置
         */
        private final int columnPosition;
        /**
         * 参数名称，添加了 @Param 注解时才有
         */
        private final String paramName;
        /**
         * Java 属性名称
         */
        private final String propertyName;
        /**
         * 类型处理器
         */
        private TypeHandler<?> typeHandler;

        protected KeyAssigner(Configuration configuration, ResultSetMetaData rsmd, int columnPosition, String paramName,
                              String propertyName) {
            super();
            this.configuration = configuration;
            this.rsmd = rsmd;
            this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            this.columnPosition = columnPosition;
            this.paramName = paramName;
            this.propertyName = propertyName;
        }

        protected void assign(ResultSet rs, Object param) {
            if (paramName != null) {
                // If paramName is set, param is ParamMap
                param = ((ParamMap<?>) param).get(paramName);
            }
            MetaObject metaParam = configuration.newMetaObject(param);
            try {
                if (typeHandler == null) {
                    if (metaParam.hasSetter(propertyName)) {
                        Class<?> propertyType = metaParam.getSetterType(propertyName);
                        // 根据 Java Type 和 Jdbc Type 获取对应的类型处理器
                        typeHandler = typeHandlerRegistry.getTypeHandler(propertyType, JdbcType.forCode(rsmd.getColumnType(columnPosition)));
                    } else {
                        throw new ExecutorException("No setter found for the keyProperty '" + propertyName + "' in '"
                            + metaParam.getOriginalObject().getClass().getName() + "'.");
                    }
                }
                if (typeHandler == null) {
                    // Error?
                } else {
                    // 将 Jdbc Type 转换成 Java Type
                    Object value = typeHandler.getResult(rs, columnPosition);
                    // 将该属性值设置到 入参对象中
                    metaParam.setValue(propertyName, value);
                }
            } catch (SQLException e) {
                throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e,
                    e);
            }
        }
    }
}
