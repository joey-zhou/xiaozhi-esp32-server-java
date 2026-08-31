package com.xiaozhi.file;

import com.xiaozhi.common.annotation.SignedFileUrl;
import com.xiaozhi.common.model.resp.PageResp;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * {@link SignedFileUrl} 字段处理工具。
 * <p>
 * 供请求/响应两个方向的 Advice 复用：读边界签名、写边界剥签名，仅转换逻辑不同。
 * 自动展开 {@link PageResp} 与集合，遍历带注解的 String 字段并用给定函数替换其值。
 */
final class SignedFileUrlSupport {

    private SignedFileUrlSupport() {
    }

    /** 缓存每个类的 @SignedFileUrl String 字段，避免重复反射 */
    private static final Map<Class<?>, List<Field>> SIGNED_FIELDS_CACHE = new ConcurrentHashMap<>();

    /**
     * 对 data（含分页/集合/单对象）中所有 @SignedFileUrl String 字段应用 transformer。
     */
    static void apply(Object data, UnaryOperator<String> transformer) {
        if (data == null) return;
        if (data instanceof PageResp<?> pageResp) {
            applyCollection(pageResp.getList(), transformer);
        } else if (data instanceof Collection<?> collection) {
            applyCollection(collection, transformer);
        } else {
            applyObject(data, transformer);
        }
    }

    private static void applyCollection(Collection<?> collection, UnaryOperator<String> transformer) {
        if (collection == null) return;
        for (Object item : collection) {
            applyObject(item, transformer);
        }
    }

    private static void applyObject(Object obj, UnaryOperator<String> transformer) {
        if (obj == null) return;
        List<Field> fields = SIGNED_FIELDS_CACHE.computeIfAbsent(obj.getClass(), SignedFileUrlSupport::resolveSignedFields);
        for (Field field : fields) {
            try {
                Object value = field.get(obj);
                if (value instanceof String str && !str.isEmpty()) {
                    field.set(obj, transformer.apply(str));
                }
            } catch (IllegalAccessException ignored) {
                // setAccessible 已开启，正常不会到这里
            }
        }
    }

    /** 收集类中标注 @SignedFileUrl 的 String 字段（含父类），并开放访问 */
    private static List<Field> resolveSignedFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(SignedFileUrl.class) && field.getType() == String.class) {
                    field.setAccessible(true);
                    result.add(field);
                }
            }
        }
        return result;
    }
}
