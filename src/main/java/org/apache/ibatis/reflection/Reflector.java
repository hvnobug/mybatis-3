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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;



/* 缓存一组类定义信息,允许在属性名和getter/setter方法之间进行简单的映射。 */

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * class 类型
   */
  private final Class<?> type;
  /**
   * 可读属性(拥有get方法读private成员变量)
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性(拥有set方法读private成员变量)
   */
  private final String[] writablePropertyNames;
  /**
   * set方法,key为属性名称,value是Invoker对象
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * get方法,key为属性名称,value是Invoker对象
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * set类型,key为属性名称,value是属性类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * get类型,key为属性名称,value是属性类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认构造器
   */
  private Constructor<?> defaultConstructor;
  /**
   * 不区分大小写的属性
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    /* class类型 */
    type = clazz;
    /* 初始化默认构造器 */
    addDefaultConstructor(clazz);
    /* 初始化 getMethods 和 getTypes ，通过遍历 getting 方法 */
    addGetMethods(clazz);
    /* 初始化 setMethods 和 setTypes ，通过遍历 setting 方法。 */
    addSetMethods(clazz);
    /* 初始化 getMethods + getTypes 和 setMethods + setTypes ，通过遍历 fields 属性。 */
    addFields(clazz);
    /* 初始化可读属性,根据 getMethods 属性 */
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    /* 初始化可写属性,根据 setMethods 属性 */
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    /* 为支持不区分大小写,将属性名转化成大写 */
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    /* 获取 class 的构造器类表 */
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    /* 获取无参构造器 */
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    /* 获取 class 所有方法 */
    Method[] methods = getClassMethods(clazz);
    /* 过滤到 getting 方法 */
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    /* 解决 getting 冲突方法 */
    resolveGetterConflicts(conflictingGetters);
  }

  /* 解决 getting 方法冲突 */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    /* 遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 getting 方法 */
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      /* 遍历 getting 方法列表 */
      for (Method candidate : entry.getValue()) {
        /* winner 为 null,说明 candidate 为最匹配的 getting 方法 */
        if (winner == null) {
          winner = candidate;
          continue;
        }
        /* 获取 winner 和 candidate 的返回类型 */
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        /* 如果类型相同 */
        if (candidateType.equals(winnerType)) {
          /* 在 getClassMethods 已经对方法进行合并 */
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                + propName + " in class " + winner.getDeclaringClass()
                + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        }
        /* winnerType 是的 candidateType 子类？ */
        else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        }
        /* candidateType 是的 winnerType 子类。符合选择子类,因为子类可以修改放大返回值。 */
        else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
          throw new ReflectionException(
            "Illegal overloaded getter method with ambiguous type for property "
              + propName + " in class " + winner.getDeclaringClass()
              + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      /* 添加到 getMethods 和 getTypes 中 */
      addGetMethod(propName, winner);
    }
  }

  private void addGetMethod(String name, Method method) {
    /* 判断字段是否有效 */
    if (isValidPropertyName(name)) {
      /* 添加到 getMethods */
      getMethods.put(name, new MethodInvoker(method));
      /* 获取返回值类型 */
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      /* 添加到 getTypes */
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    /* 获取 class 所有方法 */
    Method[] methods = getClassMethods(clazz);
    /* 过滤到 setting 方法 */
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    /* 解决 setting 冲突方法 */
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    list.add(method);
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        if (setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
      + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
      + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    /* 判断字段是否有效 */
    if (isValidPropertyName(name)) {
      /* 添加到 setMethods */
      setMethods.put(name, new MethodInvoker(method));
      /* 获取参数类型 */
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      /* 添加到 setTypes */
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    /* 获取 class 字段 */
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      /* setMethods 中不包含该字段 */
      if (!setMethods.containsKey(field.getName())) {
        /* 移除对final类型检查,因为 JDK 1.5 允许通过反射修改final字段。*/
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader

        /* 获取字段的 Java 语言修饰符 */
        int modifiers = field.getModifiers();
        /* 如果字段不是常量, */
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          /*  添加到 setMethods 和 setTypes 中 */
          addSetField(field);
        }
      }
      /* 如果 getMethods 中不包含该字段 */
      if (!getMethods.containsKey(field.getName())) {
        /*  添加到 getMethods 和 getTypes 中 */
        addGetField(field);
      }
    }
    /* 添加父类字段 */
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    /* 找到所有class的方法,包括父class */
    while (currentClass != null && currentClass != Object.class) {
      /*  */
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      /* 查找接口方法,可能 class 是抽象的 */
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      /* 判断是否为 bridge 方法 */
      if (!currentMethod.isBridge()) {
        /* 获取方法签名 */
        String signature = getSignature(currentMethod);
        /*
         * 根据签名保证方法唯一
         * 检查方法是否已知,如果已知,那么必须有一个扩展类重写类该方法
         */
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    /* 方法返回类型 */
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    /* 方法名称 */
    sb.append(method.getName());
    /* 方法参数列表 */
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
