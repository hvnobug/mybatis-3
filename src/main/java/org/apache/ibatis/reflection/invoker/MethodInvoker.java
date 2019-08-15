/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

  /** getting/setting 方法对应字段的 class 类型 */
  private final Class<?> type;
  /** 要执行的方法 */
  private final Method method;

  /** 构造函数 */
  public MethodInvoker(Method method) {
    this.method = method;
    /* 判断是 getting/setting,并初始化 type */
    if (method.getParameterTypes().length == 1) {
      type = method.getParameterTypes()[0];
    } else {
      type = method.getReturnType();
    }
  }

  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
    try {
      /* 尝试调用方法 */
      return method.invoke(target, args);
    } catch (IllegalAccessException e) {
      /* 检查是否可以控制成员访问。 */
      if (Reflector.canControlMemberAccessible()) {
        /* 设置方法可以访问并调用 */
        method.setAccessible(true);
        return method.invoke(target, args);
      } else {
        throw e;
      }
    }
  }

  @Override
  public Class<?> getType() {
    return type;
  }
}
