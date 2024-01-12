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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * XML 映射构建器，建造者模式,继承 BaseBuilder
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

    /**
     * 基于 Java XPath 解析器
     */
    private final XPathParser parser;
    /**
     * Mapper 构造器助手
     */
    private final MapperBuilderAssistant builderAssistant;

    /**
     * 用来存放 sql 片段的哈希表
     * 可被其他语句引用的可重用语句块的集合，实际上就是 Configuration 全局配置中的 sqlFragments
     *
     * 例如：<sql id="userColumns"> ${alias}.id,${alias}.username,${alias}.password </sql>
     * <sql />可能在很多地方被引用
     */
    private final Map<String, XNode> sqlFragments;
    /**
     * 资源引用的地址，例如：com/aaa/bbb.xml
     */
    private final String resource;

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(reader, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        // 1. XPathParser 构造方法会将 XML 映射文件解析成 org.w3c.dom.Document 对象，传入 XMLMapperEntityResolver 解析实例对象，使用到本地的 DTD 文件
        this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(inputStream, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        super(configuration);
        // 2. 创建 MapperBuilderAssistant 对象
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
        this.parser = parser;
        this.sqlFragments = sqlFragments;
        this.resource = resource;
    }

    /**
     * 解析
     */
    public void parse() {
        // 1. 判断当前 Mapper 是否已经加载过
        if (!configuration.isResourceLoaded(resource)) {
            // 2. 处理 <mapper /> 节点
            configurationElement(parser.evalNode("/mapper"));
            // 3. 将 resource 添加到 Configuration.loadedResources 集合中保存(判断是否加载过)
            configuration.addLoadedResource(resource);
            // 4. 绑定映射器到 namespace
            bindMapperForNamespace();
        }

        // 5. 处理 ConfigurationElement 方法中解析失败的 <resultMap /> 节点
        parsePendingResultMaps();
        // 6. 解析待定的 <cache-ref/> 节点
        parsePendingCacheRefs();
        // 7. 解析待定的 sql 语句节点，Statement 对象
        parsePendingStatements();
    }

    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }

    /**
     * 配置 mapper 元素
     * 	<mapper namespace="org.mybatis.example.BlogMapper">
     * 	  <select id="selectBlog" parameterType="int" resultType="Blog">
     * 	    select * from Blog where id = #{id}
     * 	  </select>
     * 	</mapper>
     * @param context
     */
    private void configurationElement(XNode context) {
        try {
            // 1. 配置 namespace( xml 映射文件中定义的 namespace 和接口名称不相等会抛出异常)
            String namespace = context.getStringAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }
            builderAssistant.setCurrentNamespace(namespace);
            // 2. 配置 <cache-ref/> 节点
            cacheRefElement(context.evalNode("cache-ref"));
            // 3. 配置 <cache/> 节点
            cacheElement(context.evalNode("cache"));
            // 4. 配置 parameterMap（已经废弃，老式风格的参数映射）
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));
            // 5. 配置 <resultMap/> 节点(高级功能)
            resultMapElements(context.evalNodes("/mapper/resultMap"));
            // 6. 配置 <sql/> 节点（定义可重用的 SQL 代码段）
            sqlElement(context.evalNodes("/mapper/sql"));
            // 7. 配置 select|insert|update|delete 节点
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
        }
    }

    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    /**
     * 解析<select /> <insert /> <update /> <delete /> 节点
     * @param list
     * @param requiredDatabaseId
     */
    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        // 1. 遍历<select /><insert /><update /><delete />节点
        for (XNode context : list) {
            // 1. 创建 XMLStatementBuilder 对象
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                // 解析成 MappedStatement 对象
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                // 2. 解析失败，添加到configuration 中
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }

    private void parsePendingResultMaps() {
        Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
        synchronized (incompleteResultMaps) {
            Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolve();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // ResultMap is still missing a resource...
                }
            }
        }
    }

    private void parsePendingCacheRefs() {
        Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
        synchronized (incompleteCacheRefs) {
            Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolveCacheRef();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Cache ref is still missing a resource...
                }
            }
        }
    }

    private void parsePendingStatements() {
        Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
        synchronized (incompleteStatements) {
            Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().parseStatementNode();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Statement is still missing a resource...
                }
            }
        }
    }

    /**
     * 解析当前 xml 映射文件的缓存配置，将当前 namespace 缓存引用其他的 namespace 的缓存形成映射关系保存在 Configuration 全局配置对象中
     *
     * @param context
     */
    private void cacheRefElement(XNode context) {
        if (context != null) {
            // 1. 获得指向的 namespace 名字，并添加到 configuration 的 cacheRefMap 中
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
            // 2. 创建 CacheRefResolver 对象
            CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
            try {
                // 执行解析，获取引用的缓存对象到自己这里
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    /**
     * 3. 解析 cache 标签
     *
     * 解析该节点的相关配置，然后通过 MapperBuilderAssistant 构造器小助手创建一个 Cache 缓存实例，
     * 添加到 Configuration 全局配置对象中，并设置到构造器助手中，在后续构建相关对象时使用
     *
     *   <cache eviction="FIFO" flushInterval="60000" size="512" readOnly="true"/>
     *
     * @param context
     */
    private void cacheElement(XNode context) {
        if (context != null) {
            // 1. 获得负责存储的 Cache 实现类
            String type = context.getStringAttribute("type", "PERPETUAL");
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
            // 2. 获得负责过期的 Cache 实现类
            String eviction = context.getStringAttribute("eviction", "LRU");
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
            // 3. 获得 flushInterval、size、readWrite、blocking 属性
            Long flushInterval = context.getLongAttribute("flushInterval");
            Integer size = context.getIntAttribute("size");
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);
            boolean blocking = context.getBooleanAttribute("blocking", false);
            // 4. 获得 Properties 属性
            Properties props = context.getChildrenAsProperties();
            // 5. 创建 Cache 对象
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
        }
    }

    /**
     * 4. 解析 parameterMap（已经废弃，老式风格的参数映射）
     *
     * @param list
     */
    private void parameterMapElement(List<XNode> list) {
        for (XNode parameterMapNode : list) {
            String id = parameterMapNode.getStringAttribute("id");
            String type = parameterMapNode.getStringAttribute("type");
            Class<?> parameterClass = resolveClass(type);
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<>();
            for (XNode parameterNode : parameterNodes) {
                String property = parameterNode.getStringAttribute("property");
                String javaType = parameterNode.getStringAttribute("javaType");
                String jdbcType = parameterNode.getStringAttribute("jdbcType");
                String resultMap = parameterNode.getStringAttribute("resultMap");
                String mode = parameterNode.getStringAttribute("mode");
                String typeHandler = parameterNode.getStringAttribute("typeHandler");
                Integer numericScale = parameterNode.getIntAttribute("numericScale");
                ParameterMode modeEnum = resolveParameterMode(mode);
                Class<?> javaTypeClass = resolveClass(javaType);
                JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
                Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    private void resultMapElements(List<XNode> list) {
        for (XNode resultMapNode : list) {
            try {
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    private ResultMap resultMapElement(XNode resultMapNode) {
        return resultMapElement(resultMapNode, Collections.emptyList(), null);
    }

    /**
     * 解析 resultMap 节点
     *     <resultMap id="userResultMap" type="User">
     *       <id property="id" column="user_id" />
     *       <result property="username" column="username"/>
     *       <result property="password" column="password"/>
     *     </resultMap>
     * @param resultMapNode 当前节点 Node 的封装，封装成 XNode 便于操作
     * @param additionalResultMappings 继承的 ResultMap 所对应的 ResultMapping 的集合，可以通过 extend 属性配置继承哪个 ResultMap，没有继承就是空集合
     * @param enclosingType resultMap 的类型， <association/> 也会被解析成 ResultMap,那么它的 enclosingType 就是所属 ResultMap 的 Class 对象
     * @return
     */
    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) {
        // 获取当前线程的上下文
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        // 1. 获得 type 属性
        String type = resultMapNode.getStringAttribute("type", resultMapNode.getStringAttribute("ofType",
                        resultMapNode.getStringAttribute("resultType", resultMapNode.getStringAttribute("javaType"))));
        // 获得 type 对应的类
        Class<?> typeClass = resolveClass(type);
        if (typeClass == null) {
            // 从 enclosingType Class 对象获取该 property 属性的 Class 对象
            typeClass = inheritEnclosingType(resultMapNode, enclosingType);
        }
        Discriminator discriminator = null;
        // 创建 ResultMapping 集合
        List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
        // 2. 遍历 <resultMap/> 的子节点
        List<XNode> resultChildren = resultMapNode.getChildren();
        for (XNode resultChild : resultChildren) {
            if ("constructor".equals(resultChild.getName())) {
                // 2.1 解析 resultMap 的 <constructor/> 节点
                processConstructorElement(resultChild, typeClass, resultMappings);
            } else if ("discriminator".equals(resultChild.getName())) {
                // 2.2 解析 resultMap 的 <discriminator/> 节点
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            } else { // 2.3 处理其他节点
                List<ResultFlag> flags = new ArrayList<>();
                if ("id".equals(resultChild.getName())) {
                    // 为添加该 ResultMapping 添加一个 Id 标志
                    flags.add(ResultFlag.ID);
                }
                // 生成对应的 ResultMapping 对象
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        // 获得 id 属性，没有的话自动生成
        String id = resultMapNode.getStringAttribute("id", resultMapNode.getValueBasedIdentifier());
        String extend = resultMapNode.getStringAttribute("extends");
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        // 3. 创建 ResultMapResolver 对象，执行解析；解析后得到 resultMap
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            // 处理 ResultMap 并添加到 Configuration 全局配置中，
            // 内部调用 MapperBuilderAssistant 构造器小助手的addResultMap 来生成ResultMap对象的
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
        if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
            String property = resultMapNode.getStringAttribute("property");
            if (property != null && enclosingType != null) {
                MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
                return metaResultType.getSetterType(property);
            }
        } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
            return enclosingType;
        }
        return null;
    }

    /**
     * 解析 resultMap 的 constructor
     *
     * <constructor>
     *   <idArg column="blog_id" javaType="int"/>
     * </constructor>
     *
     * @param resultChild
     * @param resultType
     * @param resultMappings
     */
    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            List<ResultFlag> flags = new ArrayList<>();
            // 结果标志加上 ID 和 CONSTRUCTOR
            flags.add(ResultFlag.CONSTRUCTOR);
            if ("idArg".equals(argChild.getName())) {
                flags.add(ResultFlag.ID);
            }
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    /**
     * 解析 resultMap 的 discriminator
     *
     * <discriminator javaType="int" column="draft">
     *   <case value="1" resultType="DraftPost"/>
     * </discriminator>
     *
     * @param context
     * @param resultType
     * @param resultMappings
     * @return
     */
    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) {
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Map<String, String> discriminatorMap = new HashMap<>();
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
            discriminatorMap.put(value, resultMap);
        }
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    private void sqlElement(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            sqlElement(list, configuration.getDatabaseId());
        }
        sqlElement(list, null);
    }


    /**
     * sql 节点
     *
     *    <sql id="userColumns"> id,username,password </sql>
     * @param list
     * @param requiredDatabaseId
     */
    private void sqlElement(List<XNode> list, String requiredDatabaseId) {
        // 1. 遍历所有<sql/>节点
        for (XNode context : list) {
            // 2. 获得 databaseId 属性
            String databaseId = context.getStringAttribute("databaseId");
            // 3. 获得完整的 id 属性
            String id = context.getStringAttribute("id");
            // 设置为‘${namespace}.${id}格式
            id = builderAssistant.applyCurrentNamespace(id, false);
            // 4. 判断 databaseId 是否匹配
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                // 5. 添加到 sqlFragments 中
                sqlFragments.put(id, context);
            }
        }
    }

    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            return requiredDatabaseId.equals(databaseId);
        }
        if (databaseId != null) {
            return false;
        }
        // 如果有重名的 id
        // <sql id="userColumns"> id,username,password </sql>
        if (!this.sqlFragments.containsKey(id)) {
            return true;
        }
        // skip this fragment if there is a previous one with a not null databaseId
        XNode context = this.sqlFragments.get(id);
        return context.getStringAttribute("databaseId") == null;
    }

    /**
     * 构建 resultMap
     *
     * @param context
     * @param resultType
     * @param flags
     * @return
     */
    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
        String property;
        // 解析各种属性
        if (flags.contains(ResultFlag.CONSTRUCTOR)) {
            property = context.getStringAttribute("name");
        } else {
            property = context.getStringAttribute("property");
        }
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String nestedSelect = context.getStringAttribute("select");
        // 处理嵌套的 resultMap；解析 <resultMap /> 标签中的 <association />，<collection />，<case /> 标签，生成 ResultMap 对象
        String nestedResultMap = context.getStringAttribute("resultMap", () -> processNestedResultMappings(context, Collections.emptyList(), resultType));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        String resultSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
        // javaType 属性
        Class<?> javaTypeClass = resolveClass(javaType);
        // typeHandler 属性
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        // jdbcType 属性
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        // 又去调 builderAssistant.buildResultMapping，通过上面的属性构建一个 ResultMapping 对象
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
    }

    /**
     * 处理嵌套的 resultMap
     * @param context
     * @param resultMappings
     * @param enclosingType
     * @return
     */
    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {
        // 处理association|collection|case
        if (Arrays.asList("association", "collection", "case").contains(context.getName())
                && context.getStringAttribute("select") == null) {
            validateCollection(context, enclosingType);
            ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
            return resultMap.getId();
        }
        return null;
    }

    protected void validateCollection(XNode context, Class<?> enclosingType) {
        if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
                && context.getStringAttribute("javaType") == null) {
            MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
            String property = context.getStringAttribute("property");
            if (!metaResultType.hasSetter(property)) {
                throw new BuilderException(
                        "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
            }
        }
    }

    private void bindMapperForNamespace() {
        // 获取映射配置文件的命名空间
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                // 解析命名空间对应的类型
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
                // ignore, bound type is not required
            }
            // 是否已经加载 boundType 接口
            if (boundType != null && !configuration.hasMapper(boundType)) {
                // Spring may not know the real resource name so we set a flag
                // to prevent loading again this resource from the mapper interface
                // look at MapperAnnotationBuilder#loadXmlResource
                // 追加 namespace 前缀，并添加到 loadedResource 集合中保存
                configuration.addLoadedResource("namespace:" + namespace);
                // 注册 boundType 接口
                configuration.addMapper(boundType);
            }
        }
    }

}
