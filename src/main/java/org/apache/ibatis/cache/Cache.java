/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * SPI for cache providers.
 * <p>
 * One instance of cache will be created for each namespace.
 * <p>
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * <p>
 * MyBatis will pass the namespace as id to the constructor.
 *
 * <pre>
 * public MyCache(final String id) {
 *  if (id == null) {
 *    throw new IllegalArgumentException("Cache instances require an ID");
 *  }
 *  this.id = id;
 *  initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */

/**
 * 将为每一个命名空间创建一个缓存实例,缓存实现必须有一个构造函数，该构造函数接收一个字符串参数作为缓存 id。
 * Mybatis 将通过命名空间把 id 传给构造函数。
 */
public interface Cache {

  /**
   * @return The identifier of this cache
   */
  /**
   * 缓存 id
   */
  String getId();

  /**
   * @param key Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  /**
   * 插入缓存
   *
   * @param key   缓存的 key
   * @param value 缓存的值
   */
  void putObject(Object key, Object value);

  /**
   * @param key The key
   * @return The object stored in the cache.
   */
  /**
   * 根据缓存的 key 获取对应的 value
   * @param key key
   * @return value
   */
  Object getObject(Object key);

  /**
   * As of 3.3.0 this method is only called during a rollback
   * for any previous value that was missing in the cache.
   * This lets any blocking cache to release the lock that
   * may have previously put on the key.
   * A blocking cache puts a lock when a value is null
   * and releases it when the value is back again.
   * This way other threads will wait for the value to be
   * available instead of hitting the database.
   *
   * @param key The key
   * @return Not used
   */
  /**
   * 移除缓存
   *
   * @param key 缓存的 key
   * @return 返回被移除的缓存对象
   */
  Object removeObject(Object key);

  /**
   * Clears this cache instance.
   */
  /**
   * 清除缓存实例
   */
  void clear();

  /**
   * Optional. This method is not called by the core.
   *
   * @return The number of elements stored in the cache (not its capacity).
   */
  /**
   * 获取缓存的元素数量
   * @return
   */
  int getSize();

  /**
   * Optional. As of 3.2.6 this method is no longer called by the core.
   * <p>
   * Any locking needed by the cache must be provided internally by the cache provider.
   *
   * @return A ReadWriteLock
   */
  /**
   * 获取读写🔒
   * @return
   */
  default ReadWriteLock getReadWriteLock() {
    return null;
  }

}
