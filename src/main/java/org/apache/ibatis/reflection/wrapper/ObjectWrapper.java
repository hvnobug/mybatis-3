/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /** 根据属性分词器获取值 */
  Object get(PropertyTokenizer prop);

  /** 根据属性分词器设置值 */
  void set(PropertyTokenizer prop, Object value);

  /** 根据表达式获取属性,可设置是否下划线转驼峰 */
  String findProperty(String name, boolean useCamelCaseMapping);

  /** MetaClass.getGetterNames() */
  String[] getGetterNames();

  /** MetaClass.getSetterNames() */
  String[] getSetterNames();

  /** MetaClass.getSetterType() */
  Class<?> getSetterType(String name);

  /** MetaClass.getGetterType() */
  Class<?> getGetterType(String name);

  /** MetaClass.hasSetter() */
  boolean hasSetter(String name);

  /** MetaClass.hasGetter() */
  boolean hasGetter(String name);

  /** MetaObject.forObject(Object, ObjectFactory, ObjectWrapperFactory, ReflectorFactory) */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /** 是否是 Collection */
  boolean isCollection();

  /** 添加元素到集合 */
  void add(Object element);

  /** 添加多个元素到集合 */
  <E> void addAll(List<E> element);

}
