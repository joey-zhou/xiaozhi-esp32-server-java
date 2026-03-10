package com.xiaozhi.common.cache;

import com.xiaozhi.entity.SysDevice;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 智能缓存 Key 生成器
 * 自动识别参数类型并生成合适的缓存 Key
 * <p>
 * 支持的类型：
 * - SysDevice: 提取 deviceId 并转换格式（: → -）
 * - String: 直接返回
 * - 其他类型：尝试反射获取 deviceId 或 id 字段
 * </p>
 *
 * @author Joey
 */
@Component("smartKeyGenerator")
public class SmartKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params == null || params.length == 0) {
            return method.getName();
        }

        // 单参数场景
        if (params.length == 1) {
            return generateKeyForParam(params[0]);
        }

        // 多参数场景，拼接所有参数的 key
        return Arrays.stream(params)
                .map(this::generateKeyForParam)
                .collect(Collectors.joining("::"));
    }

    /**
     * 根据参数类型生成 key
     */
    private String generateKeyForParam(Object param) {
        if (param == null) {
            return "null";
        }

        // SysDevice 类型：提取 deviceId 并转换格式
        if (param instanceof SysDevice) {
            String deviceId = ((SysDevice) param).getDeviceId();
            return deviceId != null ? deviceId.replace(":", "-") : "null";
        }

        // String 类型：直接返回
        if (param instanceof String) {
            return (String) param;
        }

        // 其他类型：尝试反射获取 deviceId 或 id 字段
        return extractIdField(param);
    }

    /**
     * 反射获取对象的 id 字段
     * 优先获取 deviceId 字段，其次获取 id 字段
     */
    private String extractIdField(Object param) {
        // 尝试获取 deviceId 字段
        String value = getFieldValueAsString(param, "deviceId");
        if (value != null) {
            return value.replace(":", "-");
        }

        // 尝试获取 id 字段
        value = getFieldValueAsString(param, "id");
        if (value != null) {
            return value;
        }

        // 都没有则返回 toString
        return param.toString();
    }

    /**
     * 反射获取指定字段的字符串值
     */
    private String getFieldValueAsString(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return value != null ? value.toString() : null;
        } catch (NoSuchFieldException e) {
            // 字段不存在，尝试父类
            return getSuperclassFieldValueAsString(obj, fieldName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从父类获取字段值
     */
    private String getSuperclassFieldValueAsString(Object obj, String fieldName) {
        Class<?> superClass = obj.getClass().getSuperclass();
        while (superClass != null && superClass != Object.class) {
            try {
                Field field = superClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                return value != null ? value.toString() : null;
            } catch (NoSuchFieldException e) {
                superClass = superClass.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
