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
package org.apache.ibatis.reflection.property;

import java.lang.reflect.Field;

import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
public final class PropertyCopier {

  private PropertyCopier() {
    // Prevent Instantiation of Static Class
  }

  /**
   * 复制bean的属性
   * @param type 指定类
   * @param sourceBean 来源 Bean 对象
   * @param destinationBean 目标 Bean 对象
   */
  public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
    Class<?> parent = type;
    while (parent != null) {
      /* 获取 class 字段 */
      final Field[] fields = parent.getDeclaredFields();
      /* 遍历 class 字段 */
      for (Field field : fields) {
        try {
          try {
            /* 尝试将来源字段属性复制到目标对象 */
            field.set(destinationBean, field.get(sourceBean));
          } catch (IllegalAccessException e) {
            /* 检查是否可以控制成员访问。 */
            if (Reflector.canControlMemberAccessible()) {
              field.setAccessible(true);
              /* 再次尝试将来源字段属性复制到目标对象 */
              field.set(destinationBean, field.get(sourceBean));
            } else {
              throw e;
            }
          }
        } catch (Exception e) {
          // Nothing useful to do, will only fail on final fields, which will be ignored.
        }
      }
      /* 获取父类 */
      parent = parent.getSuperclass();
    }
  }

}
