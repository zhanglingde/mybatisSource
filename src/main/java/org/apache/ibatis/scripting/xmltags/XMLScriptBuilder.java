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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * ，负责将 SQL 脚本（XML或者注解中定义的 SQL ）解析成 SqlSource 对象
 *
 * @author Clinton Begin
 */
public class XMLScriptBuilder extends BaseBuilder {
    /**
     * 当前 SQL 的 XNode 对象
     */
    private final XNode context;
    /**
     * 是否为动态 SQL
     */
    private boolean isDynamic;
    /**
     * SQL 的 Java 入参类型
     */
    private final Class<?> parameterType;
    /**
     * NodeNodeHandler 的映射
     */
    private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

    public XMLScriptBuilder(Configuration configuration, XNode context) {
        this(configuration, context, null);
    }

    public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
        super(configuration);
        this.context = context;
        this.parameterType = parameterType;
        initNodeHandlerMap();
    }


    private void initNodeHandlerMap() {
        nodeHandlerMap.put("trim", new TrimHandler());
        nodeHandlerMap.put("where", new WhereHandler());
        nodeHandlerMap.put("set", new SetHandler());
        nodeHandlerMap.put("foreach", new ForEachHandler());
        nodeHandlerMap.put("if", new IfHandler());
        nodeHandlerMap.put("choose", new ChooseHandler());
        nodeHandlerMap.put("when", new IfHandler());
        nodeHandlerMap.put("otherwise", new OtherwiseHandler());
        nodeHandlerMap.put("bind", new BindHandler());
    }

    // 将 SQL 脚本（XML或者注解中定义的 SQL ）解析成 SqlSource 对象
    public SqlSource parseScriptNode() {
        // 1. 将 SQL 解析成 MixedSqlNode 对象；（XML 或者注解中定义的 SQL）
        MixedSqlNode rootSqlNode = parseDynamicTags(context);
        SqlSource sqlSource;
        if (isDynamic) {
            // 2. 动态语句，使用了 MyBatis 自定义的 XML 标签（<if />等）或者使用了${}，则封装成 DynamicSqlSource 对象
            sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
        } else {
            // 3. 否则就是静态SQL语句，封装成 RawSqlSource 对象
            sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
        }
        return sqlSource;
    }

    // 将 SQL 脚本（XML或者注解中定义的 SQL ）解析成 MixedSqlNode 对象
    protected MixedSqlNode parseDynamicTags(XNode node) {
        List<SqlNode> contents = new ArrayList<>();
        /*
         * 1. 遍历 SQL 节点中所有子节点
         * 这里会对该节点内的所有内容进行处理然后返回 NodeList 对象
         * 1. 文本内容会被解析成 '<#text></#text>' 节点，就算一个换行符也会解析成这个
         * 2. <![CDATA[ content ]]> 会被解析成 '<#cdata-section>content</#cdata-section>' 节点
         * 3. 其他动态 <if /> <where />
         */
        NodeList children = node.getNode().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            XNode child = node.newXNode(children.item(i));
            // 子节点是 <#text /> 或 <#cdata-section /> 类型
            if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
                String data = child.getStringBody("");
                TextSqlNode textSqlNode = new TextSqlNode(data);
                if (textSqlNode.isDynamic()) {
                    // 如果是动态的 TextSqlNode 对象，也就是使用了 '${}'；标记为动态 sql
                    contents.add(textSqlNode);
                    isDynamic = true;
                } else {
                    // 如果是非动态的 TextSqlNode 对象，没有使用 '${}'
                    contents.add(new StaticTextSqlNode(data));
                }
            } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628  // 子节点是 Mybatis 自定义标签
                // 2. 根据子节点的标签，获得对应的 NodeHandler 对象
                String nodeName = child.getNode().getNodeName();
                NodeHandler handler = nodeHandlerMap.get(nodeName);
                if (handler == null) {  // 未知标签
                    throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
                }
                // 3. 执行 NodeHandler 处理并标记为动态 SQL
                handler.handleNode(child, contents);
                isDynamic = true;
            }
        }
        return new MixedSqlNode(contents);
    }

    /**
     * XMLScriptBuilder 的内部接口，用于处理 MyBatis 自定义标签
     */
    private interface NodeHandler {
        /**
         * 处理 Node
         *
         * @param nodeToHandle   要处理的 XNode 节点
         * @param targetContents 目标的 SqlNode 数组。实际上，被处理的 XNode 节点会创建成对应的 SqlNode 对象，添加到 targetContents 中
         */
        void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
    }

    /**
     * <bind />元素允许你在 OGNL 表达式(SQL语句)以外创建一个变量，并将其绑定到当前的上下文
     *
     * <select id="selectBlogsLike" resultType="Blog">
     *   <bind name="pattern" value="'%' + _parameter.getTitle() + '%'" />
     *   SELECT * FROM BLOG
     *   WHERE title LIKE #{pattern}
     * </select>
     */
    private class BindHandler implements NodeHandler {
        public BindHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析 name、value 属性
            final String name = nodeToHandle.getStringAttribute("name");
            final String expression = nodeToHandle.getStringAttribute("value");
            // 2. 根据这些属性创建 VarDeclSqlNode 对象
            final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
            // 3. 添加到targetContents集合中
            targetContents.add(node);
        }
    }

    /**
     * <trim />标签的处理器
     */
    private class TrimHandler implements NodeHandler {
        public TrimHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析<if />标签内部的子标签节点，嵌套解析，生成MixedSqlNode对象
            MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
            // 2. 获得 prefix、prefixOverrides、suffix、suffixOverrides 属性
            String prefix = nodeToHandle.getStringAttribute("prefix");
            String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
            String suffix = nodeToHandle.getStringAttribute("suffix");
            String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
            // 3. 根据上面获取到的属性创建TrimSqlNode对象
            TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
            // 4. 添加到targetContents集合中
            targetContents.add(trim);
        }
    }

    /**
     * <where />标签的处理器
     */
    private class WhereHandler implements NodeHandler {
        public WhereHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析<where /> 标签内部的子标签节点，嵌套解析，生成 MixedSqlNode 对象
            MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
            // 2. 创建 WhereSqlNode 对象，该对象继承了 TrimSqlNode，自定义前缀（WHERE）和需要删除的前缀（AND、OR等）
            WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
            targetContents.add(where);
        }
    }

    /**
     * <set />标签的处理器
     */
    private class SetHandler implements NodeHandler {
        public SetHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析<set />标签内部的子标签节点，嵌套解析，生成MixedSqlNode对象
            MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
            // 2. 创建SetSqlNode对象，该对象继承了TrimSqlNode，自定义前缀（SET）和需要删除的前缀和后缀（,）
            SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
            targetContents.add(set);
        }
    }

    /**
     * <foreach />标签的处理器
     */
    private class ForEachHandler implements NodeHandler {
        public ForEachHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析<foreach />标签内部的子标签节点，嵌套解析，生成MixedSqlNode对象
            MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
            // 2. 获得 collection、item、index、open、close、separator 属性
            String collection = nodeToHandle.getStringAttribute("collection");
            Boolean nullable = nodeToHandle.getBooleanAttribute("nullable");
            String item = nodeToHandle.getStringAttribute("item");
            String index = nodeToHandle.getStringAttribute("index");
            String open = nodeToHandle.getStringAttribute("open");
            String close = nodeToHandle.getStringAttribute("close");
            String separator = nodeToHandle.getStringAttribute("separator");
            // 3. 根据这些属性创建ForEachSqlNode对象
            ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, nullable, index, item, open, close, separator);
            targetContents.add(forEachSqlNode);
        }
    }

    /**
     * <if />标签的处理器
     */
    private class IfHandler implements NodeHandler {
        public IfHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析<if />标签内部的子标签节点，嵌套解析，生成MixedSqlNode对
            MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
            // 2. 获得 test 属性
            String test = nodeToHandle.getStringAttribute("test");
            // 3. 根据这个属性创建IfSqlNode对象
            IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
            targetContents.add(ifSqlNode);
        }
    }

    /**
     * <otherwise />标签的处理器
     */
    private class OtherwiseHandler implements NodeHandler {
        public OtherwiseHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            // 1. 解析<otherwise />标签内部的子标签节点，嵌套解析，生成MixedSqlNode对象
            MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
            // 2. 添加到targetContents集合中，需要结合ChooseHandler使用
            targetContents.add(mixedSqlNode);
        }
    }

    /**
     * <choose />标签的处理器
     */
    private class ChooseHandler implements NodeHandler {
        public ChooseHandler() {
            // Prevent Synthetic Access
        }

        @Override
        public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
            List<SqlNode> whenSqlNodes = new ArrayList<>();
            List<SqlNode> otherwiseSqlNodes = new ArrayList<>();
            // 1. 先逐步处理<choose />标签的<when /> 和 <otherwise />子标签们，通过组合 IfHandler 和 OtherwiseHandler 两个处理器，实现对子节点们的解析
            handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
            // 2. 获得 `<otherwise />` 的节点，存在多个会抛出异常
            SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
            // 3. 根据这些属性创建 ChooseSqlNode 对象
            ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
            targetContents.add(chooseSqlNode);
        }

        private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
            List<XNode> children = chooseSqlNode.getChildren();
            for (XNode child : children) {
                String nodeName = child.getNode().getNodeName();
                NodeHandler handler = nodeHandlerMap.get(nodeName);
                if (handler instanceof IfHandler) { // 处理 `<when />` 标签的情况
                    handler.handleNode(child, ifSqlNodes);
                } else if (handler instanceof OtherwiseHandler) { // 处理 `<otherwise />` 标签的情况
                    handler.handleNode(child, defaultSqlNodes);
                }
            }
        }

        private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
            SqlNode defaultSqlNode = null;
            if (defaultSqlNodes.size() == 1) {
                defaultSqlNode = defaultSqlNodes.get(0);
            } else if (defaultSqlNodes.size() > 1) {
                throw new BuilderException("Too many default (otherwise) elements in choose statement.");
            }
            return defaultSqlNode;
        }
    }

}
