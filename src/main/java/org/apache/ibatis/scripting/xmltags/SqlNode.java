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

/**
 * SQL Node接口，每个 XML Node会解析成对应的SQL Node对象，通过上下文可以对动态SQL进行逻辑处理，生成需要的结果
 * @author Clinton Begin
 */
public interface SqlNode {
    /**
     * 应用当前 SQLNode 节点
     *
     * @param context 正在解析 SQL 语句的上下文
     * @return 是否应用成功
     */
    boolean apply(DynamicContext context);
}
