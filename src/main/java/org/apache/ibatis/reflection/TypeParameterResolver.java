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

import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */

  /** 解析字段类型 */
  public static Type resolveFieldType(Field field, Type srcType) {
    /* 获取字段声明类型 */
    Type fieldType = field.getGenericType();
    /* 获取声明该字段的 Class */
    Class<?> declaringClass = field.getDeclaringClass();
    /* 返回解析到的类型 */
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  /* 解析方法返回值类型 */
  public static Type resolveReturnType(Method method, Type srcType) {
    /* 获取方法返回值类型 */
    Type returnType = method.getGenericReturnType();
    /* 获取声明该方法的 Class */
    Class<?> declaringClass = method.getDeclaringClass();
    /* 返回解析到的类型 */
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  /* 解析方法参数类型 */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    /* 获取方法参数类型列表 */
    Type[] paramTypes = method.getGenericParameterTypes();
    /* 获取声明该方法的 Class */
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      /* 获取解析到的类型放到 result 中 */
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  /** 解析类型 */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    if (type instanceof TypeVariable) {
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      return type;
    }
  }


  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {

    /* 获得这个数组元素类型 */
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /** 解析 ParameterizedType 类型 */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    /* 获取范型 <> 前面的实际类型 */
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    /* 获取范型 <> 里面的实际类型 */
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    Type[] args = new Type[typeArgs.length];
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    /* 解析泛型表达式下界（下限 super） */
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    /* 解析泛型表达式上界（上限 extends）*/
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }


  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }


  /**
   * 解析具体的类型变量指代的类型。
   * 1.如果srcType的Class类型和declaringClass为同一个类，表示获取该类型变量时被反射的类型就是其定义的类型，则取该类型变量定义是有没有上限，如果有则使用其上限代表其类型，否则就用Object。
   * 2.如果不是，则代表declaringClass是srcType的父类或者实现的接口，则解析继承中有没有定义其代表的类型
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result;
    Class<?> clazz;
    /* 判断 srcType 是否为 Class/ParameterizedType 类型,如果不是这两种类型这抛出异常 */
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }
    /* 如果declaringClass和srcType的实际类型一直则表示无法获取其类型实参。 */
    /* 如果typeVar有上限限定则返回其上限，否则返回Object处理 */
    if (clazz == declaringClass) {
      Type[] bounds = typeVar.getBounds();
      if (bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    /* 获取继承的父类类型 */
    Type superclass = clazz.getGenericSuperclass();
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    /* 如果父类的定义中没有，则处理其实现的接口。 */
    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
    /* 如果父类或者实现的接口中都没有获取到形参对应的实参，则返回 Object.class */
    return Object.class;
  }

  /**
   * 扫描父类类型
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    /* 父类类型是否是 ParameterizedType */
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      /* 获取分类真实类型 Class */
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      /* 获取 parentAsClass 的泛型类型应用说明数据示例 */
      TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();
      /* 判断 srcType 是否为 ParameterizedType 类型 */
      if (srcType instanceof ParameterizedType) {
        parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
      }
      if (declaringClass == parentAsClass) {
        for (int i = 0; i < parentTypeVars.length; i++) {
          if (typeVar == parentTypeVars[i]) {
            return parentAsType.getActualTypeArguments()[i];
          }
        }
      }
      if (declaringClass.isAssignableFrom(parentAsClass)) {
        return resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
      return resolveTypeVar(typeVar, superclass, declaringClass);
    }
    return null;
  }

  private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
    Type[] parentTypeArgs = parentType.getActualTypeArguments();
    Type[] srcTypeArgs = srcType.getActualTypeArguments();
    TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
    Type[] newParentArgs = new Type[parentTypeArgs.length];
    boolean noChange = true;
    for (int i = 0; i < parentTypeArgs.length; i++) {
      if (parentTypeArgs[i] instanceof TypeVariable) {
        for (int j = 0; j < srcTypeVars.length; j++) {
          if (srcTypeVars[j] == parentTypeArgs[i]) {
            noChange = false;
            newParentArgs[i] = srcTypeArgs[j];
          }
        }
      } else {
        newParentArgs[i] = parentTypeArgs[i];
      }
    }
    return noChange ? parentType : new ParameterizedTypeImpl((Class<?>) parentType.getRawType(), null, newParentArgs);
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {

    /** <>左边实际类型 */
    private Class<?> rawType;

    /** 所属类型 */
    private Type ownerType;

    /** <>里面实际类型 */
    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {

    /** 泛型表达式下界（下限 super） */
    private Type[] lowerBounds;

    /** 解析泛型表达式上界（上限 extends） */
    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {

    /** 数组元素类型 */
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
