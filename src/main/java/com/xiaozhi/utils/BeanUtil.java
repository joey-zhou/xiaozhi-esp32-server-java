package com.xiaozhi.utils;

import org.springframework.beans.BeanUtils;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bean 工具类
 * 替代 Hutool 的 BeanUtil
 *
 * @author Joey
 */
public class BeanUtil {
    
    // 缓存 BeanInfo，避免重复内省
    private static final Map<Class<?>, BeanInfo> BEAN_INFO_CACHE = new ConcurrentHashMap<>();
    
    // 缓存属性描述器数组
    private static final Map<Class<?>, PropertyDescriptor[]> PROPERTY_DESCRIPTORS_CACHE = new ConcurrentHashMap<>();

    /**
     * 将 Java Bean 转换为 Map
     *
     * @param bean 要转换的 Bean 对象
     * @return 包含 Bean 属性的 Map
     */
    public static Map<String, Object> beanToMap(Object bean) {
        if (bean == null) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();
        try {
            PropertyDescriptor[] propertyDescriptors = getPropertyDescriptors(bean.getClass());

            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                String name = propertyDescriptor.getName();
                Method readMethod = propertyDescriptor.getReadMethod();
                if (readMethod != null) {
                    Object value = readMethod.invoke(bean);
                    map.put(name, value);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Bean 转换为 Map 失败", e);
        }

        return map;
    }

    /**
     * 将 Map 转换为 Java Bean
     *
     * @param map 包含数据的 Map
     * @param beanClass Bean 的 Class
     * @param <T> Bean 类型
     * @return 转换后的 Bean 对象
     */
    public static <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            T bean = beanClass.getDeclaredConstructor().newInstance();
            PropertyDescriptor[] propertyDescriptors = getPropertyDescriptors(beanClass);

            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                String name = propertyDescriptor.getName();
                if (map.containsKey(name)) {
                    Method writeMethod = propertyDescriptor.getWriteMethod();
                    if (writeMethod != null) {
                        Object value = map.get(name);
                        if (value != null) {
                            writeMethod.invoke(bean, value);
                        }
                    }
                }
            }

            return bean;
        } catch (Exception e) {
            throw new RuntimeException("Map 转换为 Bean 失败", e);
        }
    }

    /**
     * 复制 Bean 属性到另一个 Bean
     * 
     * @param source 源 Bean
     * @param target 目标 Bean
     * @param <S> 源 Bean 类型
     * @param <T> 目标 Bean 类型
     * @return 目标 Bean
     */
    public static <S, T> T copyProperties(S source, T target) {
        BeanUtils.copyProperties(source,target);
        return target;
    }
    



    /**
     * 获取 Bean 的属性值
     *
     * @param bean Bean 对象
     * @param propertyName 属性名
     * @return 属性值
     */
    public static Object getProperty(Object bean, String propertyName) {
        if (bean == null || propertyName == null || propertyName.isEmpty()) {
            return null;
        }

        try {
            PropertyDescriptor propertyDescriptor = new PropertyDescriptor(propertyName, bean.getClass());
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null) {
                return readMethod.invoke(bean);
            }
        } catch (Exception e) {
            // 忽略异常，返回 null
        }

        return null;
    }

    /**
     * 设置 Bean 的属性值
     *
     * @param bean Bean 对象
     * @param propertyName 属性名
     * @param value 属性值
     */
    public static void setProperty(Object bean, String propertyName, Object value) {
        if (bean == null || propertyName == null || propertyName.isEmpty()) {
            return;
        }

        try {
            PropertyDescriptor propertyDescriptor = getPropertyDescriptor(bean.getClass(), propertyName);
            Method writeMethod = propertyDescriptor != null ? propertyDescriptor.getWriteMethod() : null;
            if (writeMethod != null) {
                writeMethod.invoke(bean, value);
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    /**
     * 获取 Bean 的所有属性描述器（带缓存）
     * 
     * @param clazz Bean 类
     * @return 属性描述器数组
     */
    private static PropertyDescriptor[] getPropertyDescriptors(Class<?> clazz) {
        return PROPERTY_DESCRIPTORS_CACHE.computeIfAbsent(clazz, key -> {
            try {
                return Introspector.getBeanInfo(clazz, Object.class).getPropertyDescriptors();
            } catch (Exception e) {
                throw new RuntimeException("获取 Bean 属性描述器失败：" + clazz.getName(), e);
            }
        });
    }
    
    /**
     * 获取 Bean 的指定属性描述器
     * 
     * @param clazz Bean 类
     * @param propertyName 属性名
     * @return 属性描述器，不存在返回 null
     */
    private static PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) {
        PropertyDescriptor[] descriptors = getPropertyDescriptors(clazz);
        for (PropertyDescriptor descriptor : descriptors) {
            if (propertyName.equals(descriptor.getName())) {
                return descriptor;
            }
        }
        return null;
    }
    
    /**
     * 清除缓存（用于动态类加载场景）
     */
    public static void clearCache() {
        BEAN_INFO_CACHE.clear();
        PROPERTY_DESCRIPTORS_CACHE.clear();
    }
}
