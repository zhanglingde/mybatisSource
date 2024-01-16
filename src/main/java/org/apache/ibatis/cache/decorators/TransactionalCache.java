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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 用来装饰二级缓存的对象，作为二级缓存一个事务的缓冲区
 * <p>
 * 在一个SqlSession会话中，该类包含所有需要添加至二级缓存的的缓存数据，当提交事务后会全部刷出到二级缓存中，或者事务回滚后移除这些缓存数据
 * <p>
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

    private static final Log log = LogFactory.getLog(TransactionalCache.class);

    /**
     * 委托的 Cache 对象。
     * <p>
     * 实际上，就是二级缓存 Cache 对象。
     */
    private final Cache delegate;

    /**
     * 提交时，清空 {@link #delegate}
     * <p>
     * 初始时，该值为 false
     * 清理后{@link #clear()} 时，该值为 true ，表示持续处于清空状态
     * <p>
     * 因为可能事务还未提交，所以不能直接清空所有的缓存，而是设置一个标记，获取缓存的时候返回 null 即可
     * 先清空下面这个待提交变量，待事务提交的时候才真正的清空缓存
     */
    private boolean clearOnCommit;

    /**
     * 待提交的 Key-Value 映射
     */
    private final Map<Object, Object> entriesToAddOnCommit;

    /**
     * 查找不到的 KEY 集合
     */
    private final Set<Object> entriesMissedInCache;

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
        this.clearOnCommit = false;
        this.entriesToAddOnCommit = new HashMap<>();
        this.entriesMissedInCache = new HashSet<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    @Override
    public Object getObject(Object key) {
        // issue #116
        Object object = delegate.getObject(key);
        if (object == null) {
            entriesMissedInCache.add(key);
        }
        // issue #146
        if (clearOnCommit) {
            return null;
        } else {
            return object;
        }
    }

    @Override
    public void putObject(Object key, Object object) {
        entriesToAddOnCommit.put(key, object);
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    @Override
    public void clear() {
        // 标记 clearOnCommit 为 true
        clearOnCommit = true;
        // 清空 entriesToAddOnCommit
        entriesToAddOnCommit.clear();
    }

    public void commit() {
        // 1. 如果 clearOnCommit 为 true ，则清空 delegate 缓存
        if (clearOnCommit) {
            delegate.clear();
        }
        // 将 entriesToAddOnCommit、entriesMissedInCache 刷入 delegate 中
        flushPendingEntries();
        reset();
    }

    public void rollback() {
        // 从 delegate 移除出 entriesMissedInCache
        unlockMissedEntries();
        reset();
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }

    private void flushPendingEntries() {
        for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
            delegate.putObject(entry.getKey(), entry.getValue());
        }
        for (Object entry : entriesMissedInCache) {
            if (!entriesToAddOnCommit.containsKey(entry)) {
                delegate.putObject(entry, null);
            }
        }
    }

    private void unlockMissedEntries() {
        for (Object entry : entriesMissedInCache) {
            try {
                delegate.removeObject(entry);
            } catch (Exception e) {
                log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
                    + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
            }
        }
    }

}
