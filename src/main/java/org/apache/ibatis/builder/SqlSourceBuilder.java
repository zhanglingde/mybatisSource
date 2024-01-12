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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * 继承了BaseBuilder抽象类，SqlSource构建器，
 * 负责将SQL语句中的#{}替换成相应的?占位符，并获取该?占位符对应的 org.apache.ibatis.mapping.ParameterMapping 对象
 *
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

    // 定义了 #{} 中支持定义哪些属性，在抛异常时使用
    private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

    public SqlSourceBuilder(Configuration configuration) {
        super(configuration);
    }

    /**
     * 执行解析原始 SQL ，成为 SqlSource 对象
     *
     * @param originalSql          原始 SQL   select * from user where user_id = #{userid}
     * @param parameterType        参数类型
     * @param additionalParameters 上下文的参数集合，包含附加参数集合（通过 <bind /> 标签生成的，或者`<foreach />`标签中的集合的元素）
     *                             RawSqlSource传入空集合
     *                             DynamicSqlSource传入 {@link org.apache.ibatis.scripting.xmltags.DynamicContext#bindings} 集合
     * @return SqlSource 对象
     */
    public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
        ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
        // 1. 创建 GenericTokenParser 对象，用于处理 #{} 中的内容，通过 handler 将其转换成 ? 占位符，并创建对应的 ParameterMapping 对象
        GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
        String sql;
        /*
         * 2. 执行解析
         * 将我们在 SQL 定义的所有占位符 #{content} 都替换成 ?
         * 并生成对应的 ParameterMapping 对象保存在 ParameterMappingTokenHandler 中
         *
         * sql：select * from user where user_id = ?
         */
        if (configuration.isShrinkWhitespacesInSql()) {
            sql = parser.parse(removeExtraWhitespaces(originalSql));
        } else {
            sql = parser.parse(originalSql);
        }
        return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
    }

    public static String removeExtraWhitespaces(String original) {
        StringTokenizer tokenizer = new StringTokenizer(original);
        StringBuilder builder = new StringBuilder();
        boolean hasMoreTokens = tokenizer.hasMoreTokens();
        while (hasMoreTokens) {
            builder.append(tokenizer.nextToken());
            hasMoreTokens = tokenizer.hasMoreTokens();
            if (hasMoreTokens) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    // 用于解析#{}的内容，创建 ParameterMapping 对象，并将其替换成 ? 占位符
    private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

        /**
         * 我们在 SQL 语句中定义的占位符对应的 ParameterMapping 数组，根据顺序来的
         */
        private final List<ParameterMapping> parameterMappings = new ArrayList<>();
        // 参数类型
        private final Class<?> parameterType;
        // additionalParameters 参数的对应的 MetaObject 对象
        private final MetaObject metaParameters;

        public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
            super(configuration);
            this.parameterType = parameterType;
            // 创建 additionalParameters 参数的对应的 MetaObject 对象
            this.metaParameters = configuration.newMetaObject(additionalParameters);
        }

        public List<ParameterMapping> getParameterMappings() {
            return parameterMappings;
        }

        @Override
        public String handleToken(String content) {
            // 构建 ParameterMapping 对象，并添加到 parameterMappings 中
            parameterMappings.add(buildParameterMapping(content));
            return "?";
        }

        /**
         * 根据内容构建一个 ParameterMapping 对象
         *
         * @param content 我们在 SQL 语句中定义的占位符
         * @return ParameterMapping 对象
         */
        private ParameterMapping buildParameterMapping(String content) {
            // 1. 将字符串解析成 key-value 键值对保存
            // 其中有一个key为"property"，value就是对应的属性名称
            Map<String, String> propertiesMap = parseParameterMapping(content);
            // 2. 获得属性的名字和类型
            String property = propertiesMap.get("property");
            Class<?> propertyType;
            if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
                propertyType = metaParameters.getGetterType(property);
            } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {  // 有对应的类型处理器，例如java.lang.string
                propertyType = parameterType;
            } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {  // 设置的 Jdbc Type 是游标
                propertyType = java.sql.ResultSet.class;
            } else if (property == null || Map.class.isAssignableFrom(parameterType)) { // 是 Map 集合
                propertyType = Object.class;
            } else {
                MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
                if (metaClass.hasGetter(property)) {
                    // 通过反射获取到其对应的 Java Type
                    propertyType = metaClass.getGetterType(property);
                } else {
                    propertyType = Object.class;
                }
            }
            // 创建 ParameterMapping.Builder 构建者对象，设置参数的名称与Java Type
            ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
            Class<?> javaType = propertyType;
            String typeHandlerAlias = null;
            // 遍历 SQL 配置的占位符信息，例如这样配置："name = #{name, jdbcType=VARCHAR}"
            for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if ("javaType".equals(name)) {
                    javaType = resolveClass(value);
                    builder.javaType(javaType);
                } else if ("jdbcType".equals(name)) {
                    builder.jdbcType(resolveJdbcType(value));
                } else if ("mode".equals(name)) {
                    builder.mode(resolveParameterMode(value));
                } else if ("numericScale".equals(name)) {
                    builder.numericScale(Integer.valueOf(value));
                } else if ("resultMap".equals(name)) {
                    builder.resultMapId(value);
                } else if ("typeHandler".equals(name)) {
                    typeHandlerAlias = value;
                } else if ("jdbcTypeName".equals(name)) {
                    builder.jdbcTypeName(value);
                } else if ("property".equals(name)) {
                    // Do Nothing
                } else if ("expression".equals(name)) {
                    throw new BuilderException("Expression based parameters are not supported yet");
                } else {
                    throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
                }
            }
            // 2. 如果TypeHandler类型处理器的别名非空，则尝试获取其对应的类型处理器并设置到Builder中
            if (typeHandlerAlias != null) {
                builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
            }
            return builder.build();
        }

        private Map<String, String> parseParameterMapping(String content) {
            try {
                return new ParameterExpression(content);
            } catch (BuilderException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
            }
        }
    }

}
