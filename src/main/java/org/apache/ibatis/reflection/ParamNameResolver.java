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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 方法参数名称解析器，用于解析我们定义的 Mapper 接口的方法
 */
public class ParamNameResolver {

    public static final String GENERIC_NAME_PREFIX = "param";

    private final boolean useActualParamName;

    /**
     * 参数名映射
     * KEY：参数顺序
     * VALUE：参数名
     *
     * <p>
     * The key is the index and the value is the name of the parameter.<br />
     * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
     * the parameter index is used. Note that this index could be different from the actual index
     * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
     * </p>
     * <ul>
     * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
     * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
     * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
     * </ul>
     */
    private final SortedMap<Integer, String> names;

    /**
     * 是否有 {@link Param} 注解的参数
     */
    private boolean hasParamAnnotation;

    public ParamNameResolver(Configuration config, Method method) {
        this.useActualParamName = config.isUseActualParamName();
        // 获取方法的参数类型集合
        final Class<?>[] paramTypes = method.getParameterTypes();
        // 获取方法参数上面的注解集合
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        // 该集合用于记录参数索引与参数名称的对应关系
        final SortedMap<Integer, String> map = new TreeMap<>();
        int paramCount = paramAnnotations.length;
        // get names from @Param annotations
        // 遍历方法参数，获取 @Param 注解标注的参数名称
        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            // 忽略 RowBounds、ResultHandler 参数类型
            if (isSpecialParameter(paramTypes[paramIndex])) {
                // skip special parameters
                continue;
            }
            String name = null;
            // 1. 首先，从 @Param 注解中获取参数名
            for (Annotation annotation : paramAnnotations[paramIndex]) {
                if (annotation instanceof Param) {
                    // 存在 @Param 注解，标记为 true
                    hasParamAnnotation = true;
                    // 获取 @Param 注解指定的参数名称
                    name = ((Param) annotation).value();
                    break;
                }
            }
            if (name == null) {
                // @Param was not specified.
                // 2. 该参数没有对应的 @Param 注解，则根据配置决定是否使用参数实际名称作为其名称（默认开启）
                if (useActualParamName) {
                    name = getActualParamName(method, paramIndex);
                }
                // 3. 最差，使用 map 的顺序，作为编号
                if (name == null) {
                    // use the parameter index as the name ("0", "1", ...)
                    // gcode issue #71
                    // 使用参数的索引作为名称
                    name = String.valueOf(map.size());
                }
            }
            // 添加到 map 中
            map.put(paramIndex, name);
        }
        // 构建不可变的 SortedMap 集合
        names = Collections.unmodifiableSortedMap(map);
    }

    private String getActualParamName(Method method, int paramIndex) {
        return ParamNameUtil.getParamNames(method).get(paramIndex);
    }

    /**
     * 过滤 RowBounds 和 ResultHandler 两种类型的参数
     * @param clazz
     * @return
     */
    private static boolean isSpecialParameter(Class<?> clazz) {
        return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
    }

    /**
     * Returns parameter names referenced by SQL providers.
     *
     * @return the names
     */
    public String[] getNames() {
        return names.values().toArray(new String[0]);
    }

    /**
     * 根据参数值返回参数名称与参数值的映射关系
     *
     * @param args 参数值数组
     * @return 参数名称与参数值的映射关系
     *
     * <p>
     * A single non-special parameter is returned without a name.
     * Multiple parameters are named using the naming rule.
     * In addition to the default names, this method also adds the generic names (param1, param2,
     * ...).
     * </p>
     *
     * @param args the args
     * @return the named params
     */
    public Object getNamedParams(Object[] args) {
        final int paramCount = names.size();
        // 无参数,返回 null
        if (args == null || paramCount == 0) {
            return null;
        } else if (!hasParamAnnotation && paramCount == 1) {
            // 未使用 @Param,且只有一个参数，则直接返回该值
            Object value = args[names.firstKey()];
            return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
        } else {
            // 处理使用 @Param 注解指定了参数名称或者多个参数的情况
            // param 这个 map 记录了参数名称与实参之间的对应关系，ParamMap 继承了 HashMap，如果向 paramMap 中添加已经存在的 key，会报错，
            /*
             * 参数名称与值的映射，包含一下两种组合数据
             * 组合1：（参数名，值）
             * 组合2：（param + 参数顺序，值）
             */
            final Map<String, Object> param = new ParamMap<>();
            int i = 0;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                // 组合 1 ：添加到 param 中
                param.put(entry.getValue(), args[entry.getKey()]);
                // add generic param names (param1, param2, ...)
                // 为参数创建param+索引格式的默认参数名称，并添加到 param 集合中
                final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
                // ensure not to overwrite parameter named with @Param
                // 如果 @param 注解指定的参数名称就是param+索引格式的，则不需要添加
                if (!names.containsValue(genericParamName)) {
                    // 再加一个#{param1},#{param2}...参数
                    //你可以传递多个参数给一个映射器方法。如果你这样做了,
                    //默认情况下它们将会以它们在参数列表中的位置来命名,比如:#{param1},#{param2}等。
                    //如果你想改变参数的名称(只在多参数情况下) ,那么你可以在参数上使用@Param(“paramName”)注解。
                    // 组合 2 ：添加到 param 中
                    param.put(genericParamName, args[entry.getKey()]);
                }
                i++;
            }
            return param;
        }
    }

    /**
     * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
     *
     * @param object          a parameter object
     * @param actualParamName an actual parameter name
     *                        (If specify a name, set an object to {@link ParamMap} with specified name)
     * @return a {@link ParamMap}
     * @since 3.5.5
     */
    public static Object wrapToMapIfCollection(Object object, String actualParamName) {
        if (object instanceof Collection) {
            // 参数若是 Collection 型，做 collection 标记
            ParamMap<Object> map = new ParamMap<>();
            map.put("collection", object);
            if (object instanceof List) {
                // 参数若是 List 类型，做 list 标记
                map.put("list", object);
            }
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        } else if (object != null && object.getClass().isArray()) {
            // 参数若是数组型，，做array标记
            ParamMap<Object> map = new ParamMap<>();
            map.put("array", object);
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        }
        // 参数若不是集合型，直接返回原来值
        return object;
    }

}
