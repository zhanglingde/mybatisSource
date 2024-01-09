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

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * 在执行数据库更新操作时，需要通过 KeyGenerator 来进行前置处理或者后置处理，我一般用于自增主键
 *
 * @author Clinton Begin
 */
public interface KeyGenerator {

    /**
     * 在 SQL 执行后设置自增键到入参中
     *
     * @param executor  执行器
     * @param ms        MappedStatement 对象
     * @param stmt      Statement对象
     * @param parameter 入参对象
     */
    void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

    /**
     * 在 SQL 执行前设置自增键到入参中
     *
     * @param executor  执行器
     * @param ms        MappedStatement 对象
     * @param stmt      Statement对象
     * @param parameter 入参对象
     */
    void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
