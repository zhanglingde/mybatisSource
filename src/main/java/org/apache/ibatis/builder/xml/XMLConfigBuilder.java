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
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * 根据配置文件进行解析，开始 Mapper 接口与 XML 映射文件的初始化，生成 Configuration 全局配置对象
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

    /**
     * 标识是否已经解析过 xml 配置文件
     */
    private boolean parsed;
    /**
     * 用于解析 xml 配置文件的对象
     */
    private final XPathParser parser;
    /**
     * 标识 <environment> 配置的名称
     */
    private String environment;
    /**
     * ReflectorFactory 负责创建和缓存 Reflector 对象
     */
    private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    // 以下 3 个一组
    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    // 以下 3 个一组
    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    /**
     * 上面 6 个构造函数最后都会合流到这个函数，传入 XPathParser
     * @param parser
     * @param environment
     * @param props
     */
    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        // 1. 创建 Configuration 对象
        super(new Configuration());
        // 创建一个当前线程的上下文，记录错误信息
        // 上下文设置成 SQL Mapper Configuration(xml 文件配置)
        ErrorContext.instance().resource("SQL Mapper Configuration");
        // 2. 设置 Configuration 的 variables 属性
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    /**
     * 解析配置文件
     * @return
     */
    public Configuration parse() {
        // 1. 若已解析，抛出 BuilderException 异常
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        // 标记已解析
        parsed = true;
        // 2. 解析 XML <configuration> 根节点
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 解析配置文件根节点
     * @param root
     */
    private void parseConfiguration(XNode root) {
        try {
            // 分步骤解析
            // issue #117 read properties first
            // 1. 解析 <properties /> 标签
            propertiesElement(root.evalNode("properties"));
            // 2. 解析 <settings /> 标签，解析配置生成 Properties 对象
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            // 根据配置加载自定义 VFS 实现类
            loadCustomVfs(settings);
            // 根据配置加载自定义的 Log 实现类
            loadCustomLogImpl(settings);
            // 3. 解析 <typeAliases /> 标签，生成别名与类的映射关系
            typeAliasesElement(root.evalNode("typeAliases"));
            // 4. 解析 <plugins /> 标签，添加自定义拦截器插件
            pluginElement(root.evalNode("plugins"));
            // 5. 解析 <objectFactory /> 标签，自定义实例工厂
            objectFactoryElement(root.evalNode("objectFactory"));
            // 6. 解析 <objectWrapperFactory /> 标签，自定义 ObjectWrapperFactory 工厂，无默认实现
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            // 7. 解析 <reflectorFactory /> 标签，自定义 Reflector 工厂
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            // 将 <settings /> 配置信息添加到 Configuration 属性
            settingsElement(settings);
            // read it after objectFactory and objectWrapperFactory issue #631
            // 8. 解析 <environments /> 标签，自定义当前环境信息
            environmentsElement(root.evalNode("environments"));
            // 9. 解析 <databaseIdProvider /> 标签，数据库标识符
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            // 10. 解析 <typeHandlers /> 标签，自定义类型处理器
            typeHandlerElement(root.evalNode("typeHandlers"));
            // 11. 解析 <mappers /> 标签，扫描Mapper接口并进行解析（重要）
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        // 解析 settings 子节点的 name 和 value 属性，并返回 properties 对象
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        // 创建 Configuration 对应的 MetaClass 对象
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        // 检测 Configuration 中是否定义了 key 指定属性的 setter 方法
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    private void loadCustomVfs(Properties props) throws ClassNotFoundException {
        String value = props.getProperty("vfsImpl");
        if (value != null) {
            String[] clazzes = value.split(",");
            for (String clazz : clazzes) {
                if (!clazz.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }
    }

    private void loadCustomLogImpl(Properties props) {
        Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
        configuration.setLogImpl(logImpl);
    }

    /**
     * 2. 解析类型别名标签
     * @param parent
     */
    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    // 扫描指定包下的所有类，并解析 @Alias 注解，完成别名注册(有@Alias注解则用，没有则取类的simpleName)
                    String typeAliasPackage = child.getStringAttribute("name");
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    // 如果是 typeAlias
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        // 根据 Class 名字来注册类型别名
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            // 扫描 @Alias 注解，完成注册
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            // 注册别名
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }

    /**
     * 3. 解析插件标签
     *
     * @param parent
     * @throws Exception
     */
    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                String interceptor = child.getStringAttribute("interceptor");
                // 获取 plugins 节点下的 properties 配置的信息，并形成 properties 对象
                Properties properties = child.getChildrenAsProperties();
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
                interceptorInstance.setProperties(properties);
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    /**
     * 4. 对象工厂，可以自定义对象创建的方式,比如用对象池？
     *
     *      <objectFactory type="org.mybatis.example.ExampleObjectFactory">
     *        <property name="someProperty" value="100"/>
     *      </objectFactory>
     * @param context
     * @throws Exception
     */
    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            // 进行别名解析后，实例化自定义的 ObjectFactory
            ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            // 设置自定义 objectFactory 的属性，完成初始化的相关操作
            factory.setProperties(properties);
            // 将自定义的 ObjectFactory 对象设置到 Configuration 对象中
            configuration.setObjectFactory(factory);
        }
    }

    // 5. 对象包装工厂
    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    /**
     * 6. 反射工厂
     *
     * @param context
     * @throws Exception
     */
    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    /**
     * 1. 解析属性
     *   <properties resource="org/mybatis/example/config.properties">
     *       <property name="username" value="dev_user"/>
     *       <property name="password" value="F2Fa3!33TYyg"/>
     *   </properties>
     *
     * @param context
     * @throws Exception
     */
    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // 解析 properties 标签子节点的 name 和 value 属性，并设置到 Properties 中
            Properties defaults = context.getChildrenAsProperties();
            // 解析 properties 的 resource 和 url 属性，这两个属性用于确定 properties 配置文件的位置
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            // 不能同时存在
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            // 与 Configuration 对象中的 variables 集合合并
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            // 更新 XPathParser 和 Configuration 的 variables 字段
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    /**
     * 6. 解析 settings 节点
     *
     * 这些是极其重要的调整, 它们会修改 MyBatis 在运行时的行为方式
     *   <settings>
     *     <setting name="cacheEnabled" value="true"/>
     *     <setting name="lazyLoadingEnabled" value="true"/>
     *     <setting name="multipleResultSetsEnabled" value="true"/>
     *     <setting name="useColumnLabel" value="true"/>
     *     <setting name="useGeneratedKeys" value="false"/>
     *     <setting name="enhancementEnabled" value="false"/>
     *     <setting name="defaultExecutorType" value="SIMPLE"/>
     *     <setting name="defaultStatementTimeout" value="25000"/>
     *     <setting name="safeRowBoundsEnabled" value="false"/>
     *     <setting name="mapUnderscoreToCamelCase" value="false"/>
     *     <setting name="localCacheScope" value="SESSION"/>
     *     <setting name="jdbcTypeForNull" value="OTHER"/>
     *     <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
     *   </settings>
     * @param props
     */
    private void settingsElement(Properties props) {
        // 如何自动映射列到字段/属性
        configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        // 自动映射不知道的列
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        // 缓存
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        // proxyFactory (CGLIB | JAVASSIST)
        // 延迟加载的核心技术就是用代理模式，CGLIB/JAVASSIST两者选一
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
        // 延迟加载
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        // 延迟加载时，每种属性是否还需要按需加载
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        // 是否允许多种结果集从一个单独的语句中返回
        configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        // 使用列标签代替列名
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        // 允许 JDBC 支持生成的键
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        // 配置默认的执行器
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        // 超时时间
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        // 默认获取的结果条数
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        // 默认结果集合的类型
        configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
        // 是否将 DB 字段自动映射到驼峰式 Java 属性中
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        // 嵌套语句上使用 RowBounds
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        // 默认用 session 级别的缓存
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        // 为 null 值设置 jdbcType
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        // Object 的那些方法将触发延迟加载
        configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        // 使用安装的 ResultHandler
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        // 动态 sql 生成语言所使用的脚本语言
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        // 枚举类型处理器
        configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
        // 当结果集中含有 Null 值时是否执行映射对象的 setter 或 Map 对象的 put 方法。此设置对于原始类型如int,boolean等无效。
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        // 是否使用实际的参数名称
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        // logger 名字的前缀
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        // 配置工厂
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
        configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
        configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
    }

    /**
     * 7. 解析环境节点
     *
     *  	<environments default="development">
     *   	  <environment id="development">
     *   	    <transactionManager type="JDBC">
     *   	      <property name="..." value="..."/>
     *   	    </transactionManager>
     *   	    <dataSource type="POOLED">
     *   	      <property name="driver" value="${driver}"/>
     *   	      <property name="url" value="${url}"/>
     *   	      <property name="username" value="${username}"/>
     *   	      <property name="password" value="${password}"/>
     *   	    </dataSource>
     *   	  </environment>
     *   	</environments>
     *
     * @param context
     * @throws Exception
     */
    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            if (environment == null) {
                environment = context.getStringAttribute("default");
            }
            // 遍历 environments 下的子节点
            for (XNode child : context.getChildren()) {
                String id = child.getStringAttribute("id");
                // 循环比较 id 是否就是指定的 environment
                if (isSpecifiedEnvironment(id)) {
                    // 7.1 事务管理器
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    // 7.2 数据源
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    configuration.setEnvironment(environmentBuilder.build());
                    break;
                }
            }
        }
    }

    /**
     * 9. databaseIdProvider
     * 可以根据不同的数据库执行不同的 sql，sql 要加 databaseId 属性
     * 这个功能感觉不是很实用，真要多数据库支持，那SQL工作量将会成倍增长，用mybatis以后一般就绑死在一个数据库上了。但也是一个不得已的方法吧
     * 可以参考org.apache.ibatis.submitted.multidb包里的测试用例
     * 	<databaseIdProvider type="VENDOR">
     * 	  <property name="SQL Server" value="sqlserver"/>
     * 	  <property name="DB2" value="db2"/>
     * 	  <property name="Oracle" value="oracle" />
     * 	</databaseIdProvider>
     * @param context
     * @throws Exception
     */
    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            // 兼容老版本
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
            // 配置 DatabaseIdProvider，完成初始化
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            // 得到当前的databaseId，可以调用DatabaseMetaData.getDatabaseProductName()得到诸如"Oracle (DataDirect)"的字符串，
            // 然后和预定义的property比较,得出目前究竟用的是什么数据库
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    /**
     * 7.1 解析事务管理器节点
     *  <transactionManager type="JDBC">
     *     <property name="..." value="..."/>
     *  </transactionManager>
     * @param context
     * @return
     * @throws Exception
     */
    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 根据 type="JDBC" 返回适当的 TransactionFactory
            TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    /**
     * 7.2 解析数据源节点
     *
     *      <dataSource type="POOLED">
     * 		    <property name="driver" value="com.mysql.jdbc.Driver"/>
     * 		    <property name="url" value="jdbc:mysql://127.0.0.1:3306/db_activiti6"/>
     * 		    <property name="username" value="root"/>
     * 		    <property name="password" value="zhangling03"/>
     * 		</dataSource>
     * @param context
     * @return
     * @throws Exception
     */
    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 根据 type="POOLED" 解析返回适当的 DataSourceFactory
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    /**
     * 9. 解析类型处理器
     *
     *   <typeHandlers>
     *       <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
     *   </typeHandlers>
     *      OR
     *   <typeHandlers>
     *   	 <package name="org.mybatis.example"/>
     *   </typeHandlers>
     *
     * @param parent
     */
    private void typeHandlerElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    // 配置 package 属性，就去找包下所有类
                    String typeHandlerPackage = child.getStringAttribute("name");
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    // 配置的是 typeHandler
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    // 三种不同的参数形式
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    /**
     * 10. 映射器
     *
     *  1. 使用类路径
     *      <mappers>
     *    	  <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
     *    	  <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
     *    	  <mapper resource="org/mybatis/builder/PostMapper.xml"/>
     *    	</mappers>
     *  2. 使用绝对 url 路径
     *      <mappers>
     *   	    <mapper url="file:///var/mappers/AuthorMapper.xml"/>
     *   	    <mapper url="file:///var/mappers/BlogMapper.xml"/>
     *   	    <mapper url="file:///var/mappers/PostMapper.xml"/>
     *   	  </mappers>
     *  3. 使用 java 类名
     *      <mappers>
     *   	    <mapper class="org.mybatis.builder.AuthorMapper"/>
     *   	    <mapper class="org.mybatis.builder.BlogMapper"/>
     *   	    <mapper class="org.mybatis.builder.PostMapper"/>
     *   	  </mappers>
     *  4. 自动扫描包下所有映射器
     *      <mappers>
     *   	    <package name="org.mybatis.builder"/>
     *   	  </mappers>
     * @param parent
     * @throws Exception
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            // 0. 处理 mapper 子节点
            for (XNode child : parent.getChildren()) {
                // 1. 如果是 package 标签，则扫描该包
                if ("package".equals(child.getName())) {
                    // 获得包名，添加到 configuration 中
                    String mapperPackage = child.getStringAttribute("name");
                    // 扫描指定的包，并向 mapperRegistry 注册 mapper 接口
                    configuration.addMappers(mapperPackage);
                } else {    // 如果是 mapper 标签
                    // 获取 mapper 节点的 resource、url、class 属性，三个属性互斥
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    // 2. 使用相对于类路径的资源引用
                    // 如果 mapper 节点指定了 resource 或 url 属性，则创建 XmlMapperBuilder 对象，并通过该对象解析resource或者url属性指定的mapper配置文件
                    if (resource != null && url == null && mapperClass == null) {
                        // 2.1. 使用类路径
                        ErrorContext.instance().resource(resource);
                        // 获得 resource 的 InputStream 对象
                        try(InputStream inputStream = Resources.getResourceAsStream(resource)) {
                            // 创建 XMLMapperBuilder 对象，并执行解析
                            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                            mapperParser.parse();
                        }
                    // 3. 使用完全限定资源定位符（URL）
                    } else if (resource == null && url != null && mapperClass == null) {
                        // 3.1. 获得 url 的 InputStream 对象（使用绝对 url 路径）
                        ErrorContext.instance().resource(url);
                        try(InputStream inputStream = Resources.getUrlAsStream(url)){
                            // 创建 XMLMapperBuilder 对象，并执行解析
                            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                            mapperParser.parse();
                        }
                    // 4. 使用映射器接口实现类的完全限定类名（使用 java 类名）
                    } else if (resource == null && url == null && mapperClass != null) {
                        // 获得 Mapper 接口 （如果 mapper 节点指定了 class 属性，则向 MapperRegistry 注册该 mapper 接口）
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        // 将映射加入配置文件
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    /**
     * 比较 id 和 environment 是否相等
     * @param id
     * @return
     */
    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        }
        if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        }
        return environment.equals(id);
    }

}
