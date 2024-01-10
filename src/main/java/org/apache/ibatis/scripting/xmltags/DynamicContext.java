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
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * 解析动态 SQL 语句时的上下文，用于解析 SQL 时，记录动态 SQL 处理后的 SQL 语句，内部提供 ContextMap 对象保存上下文的参数
 *
 * @author Clinton Begin
 */
public class DynamicContext {

    /**
     * 入参保存在 ContextMap 中的 Key
     *
     * {@link #bindings}
     */
    public static final String PARAMETER_OBJECT_KEY = "_parameter";
    /**
     * 数据库编号保存在 ContextMap 中的 Key
     *
     * {@link #bindings}
     */
    public static final String DATABASE_ID_KEY = "_databaseId";

    static {
        // 设置 OGNL 的属性访问器
        OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
    }

    /**
     * 上下文的参数集合，包含附加参数（通过`<bind />`标签生成的，或者`<foreach />`标签中的集合的元素等等）
     */
    private final ContextMap bindings;
    /**
     * 生成后的 SQL
     */
    private final StringJoiner sqlBuilder = new StringJoiner(" ");
    /**
     * 唯一编号。在 {@link org.apache.ibatis.scripting.xmltags.XMLScriptBuilder.ForEachHandler} 使用
     */
    private int uniqueNumber = 0;

    public DynamicContext(Configuration configuration, Object parameterObject) {
        // 1. 初始化 bindings 参数
        if (parameterObject != null && !(parameterObject instanceof Map)) {
            // 根据入参转换成MetaObject对象
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            // 入参类型是否有对应的类型处理器
            boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
            bindings = new ContextMap(metaObject, existsTypeHandler);
        } else {
            bindings = new ContextMap(null, false);
        }
        // 2. 添加 bindings 的默认值：_parameter > 入参对象，_databaseId -> 数据库标识符
        bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
        bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }

    public void bind(String name, Object value) {
        bindings.put(name, value);
    }

    public void appendSql(String sql) {
        sqlBuilder.add(sql);
    }

    public String getSql() {
        return sqlBuilder.toString().trim();
    }

    public int getUniqueNumber() {
        return uniqueNumber++;
    }

    /**
     * DynamicContext的内部静态类，继承HashMap，用于保存解析动态SQL语句时的上下文的参数集合
     */
    static class ContextMap extends HashMap<String, Object> {
        private static final long serialVersionUID = 2977601501966151582L;
        /**
         * parameter 对应的 MetaObject 对象
         */
        private final MetaObject parameterMetaObject;
        /**
         * 是否有对应的类型处理器
         */
        private final boolean fallbackParameterObject;

        public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
            this.parameterMetaObject = parameterMetaObject;
            this.fallbackParameterObject = fallbackParameterObject;
        }

        // 重写了 HashMap 的 get(Object key) 方法，增加支持对 parameterMetaObject 属性的访问
        @Override
        public Object get(Object key) {
            String strKey = (String) key;
            if (super.containsKey(strKey)) {
                return super.get(strKey);
            }

            if (parameterMetaObject == null) {
                return null;
            }

            if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
                return parameterMetaObject.getOriginalObject();
            } else {
                // issue #61 do not modify the context when reading
                return parameterMetaObject.getValue(strKey);
            }
        }
    }

    // DynamicContext 的内部静态类，实现 ognl.PropertyAccessor 接口，上下文访问器
    static class ContextAccessor implements PropertyAccessor {

        @Override
        public Object getProperty(Map context, Object target, Object name) {
            Map map = (Map) target;

            // 1. 在重写的getProperty方法中，先从 ContextMap 里面获取属性值
            Object result = map.get(name);
            if (map.containsKey(name) || result != null) {
                return result;
            }

            // 2. 没有获取到则获取 PARAMETER_OBJECT_KEY 属性的值，如果是 Map 类型，则从这里面获取属性值
            Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
            if (parameterObject instanceof Map) {
                return ((Map) parameterObject).get(name);
            }

            return null;
        }

        @Override
        public void setProperty(Map context, Object target, Object name, Object value) {
            Map<Object, Object> map = (Map<Object, Object>) target;
            map.put(name, value);
        }

        @Override
        public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
            return null;
        }

        @Override
        public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
            return null;
        }
    }
}
