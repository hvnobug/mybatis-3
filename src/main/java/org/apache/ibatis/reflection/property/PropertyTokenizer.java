/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {

  /** 当前字符串 */
  private String name;
  /** 索引的 name ，因为 name 如果存在 index 会被更改 */
  private final String indexedName;
  /** 索引 */
  private String index;
  /** 剩余字符串 */
  private final String children;

  /**
   * 构造器
   * @param fullname
   */
  public PropertyTokenizer(String fullname) {
    /* 查找 fullname 中'.'的位置来初始化 name、children */
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    /* 记录当前 name */
    indexedName = name;
    /* 若存在 [ ，则获得 index ，并修改 name 。*/
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  /* 是否可以继续迭代 */
  public boolean hasNext() {
    return children != null;
  }

  @Override
  /* 迭代获得下一个 PropertyTokenizer 对象 */
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  /* 不支持移除 */
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
