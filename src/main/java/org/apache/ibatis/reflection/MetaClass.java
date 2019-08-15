/**
 * Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class MetaClass {

  /**
   * 反射器工厂
   */
  private final ReflectorFactory reflectorFactory;

  /**
   * 反射器
   */
  private final Reflector reflector;

  /**
   * 构造函数
   */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 根据属性名创建返回值类型的 MetaClass 对象
   *
   * @param name 属性名
   * @return MetaClass 对象
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 根据表达式，获得属性
   *
   * @param name 表达式
   * @return 属性
   */
  public String findProperty(String name) {
    /* 根据表达式构建属性 */
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 根据表达式，获得属性
   *
   * @param name                表达式
   * @param useCamelCaseMapping 是否要下划线转驼峰
   * @return 属性
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 获取指定属性 getting 方法返回值类型
   *
   * @param name 属性名称
   * @return
   */
  public Class<?> getGetterType(String name) {
    /* 初始化属性分词器 */
    PropertyTokenizer prop = new PropertyTokenizer(name);
    /* 是否有子表达式 */
    if (prop.hasNext()) {
      /* 初始化 MetaClass 对象 */
      MetaClass metaProp = metaClassForProperty(prop);
      /* 递归获取子表达式 children 中 getting 方法返回值类型  */
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    /* 解析属性 getting 方法返回值类型 */
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 根据属性分词器获取 getting 方法返回值类型
   *
   * @param prop 属性分词器
   * @return
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    /* 获取属性的 返回值类型 */
    Class<?> type = reflector.getGetterType(prop.getName());
    /* 判断是否为 Collection */
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      /* 获取返回类型 */
      Type returnType = getGenericGetterType(prop.getName());
      /* 是否是范型类型 */
      if (returnType instanceof ParameterizedType) {
        /* 获取实际类型参数 */
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }


  private Type getGenericGetterType(String propertyName) {
    try {
      /* 获取 Invoker 对象 */
      Invoker invoker = reflector.getGetInvoker(propertyName);
      /* 如果 MethodInvoker 对象，则说明是 getting 方法，解析方法返回类型 */
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      }
      /* 如果 GetFieldInvoker 对象，则说明是 field ，直接访问 */
      else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  /**
   * 判断指定属性是否有 setting 方法
   *
   * @param name 属性
   * @return
   */
  public boolean hasSetter(String name) {
    /* 初始化属性分词器 */
    PropertyTokenizer prop = new PropertyTokenizer(name);
    /* 是否有子表达式 */
    if (prop.hasNext()) {
      /* 属性是否有 setting 方法 */
      if (reflector.hasSetter(prop.getName())) {
        /* 初始化 MetaClass 对象 */
        MetaClass metaProp = metaClassForProperty(prop.getName());
        /* 递归判断子表达式 children ，是否有 setting 方法 */
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      /* 属性是否有 setting 方法 */
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 判断指定属性是否有 getting 方法
   *
   * @param name 属性
   * @return
   */
  public boolean hasGetter(String name) {
    /* 初始化属性分词器 */
    PropertyTokenizer prop = new PropertyTokenizer(name);
    /* 是否有子表达式 */
    if (prop.hasNext()) {
      /* 属性是否有 getting 方法 */
      if (reflector.hasGetter(prop.getName())) {
        /* 初始化 MetaClass 对象 */
        MetaClass metaProp = metaClassForProperty(prop);
        /* 递归判断子表达式 children ，是否有 getting 方法 */
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      /* 属性是否有 getting 方法 */
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  private StringBuilder buildProperty(String name, StringBuilder builder) {
    /* 创建属性分词器 */
    PropertyTokenizer prop = new PropertyTokenizer(name);
    /* 是否有子表达式 */
    if (prop.hasNext()) {
      /* 反射器获取对应属性名 */
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        /* 拼接到 builder */
        builder.append(propertyName);
        builder.append(".");
        /* 创建 MetaClass 对象 */
        MetaClass metaProp = metaClassForProperty(propertyName);
        /* 递归调用 buildProperty */
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        /* 拼接到 builder */
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
