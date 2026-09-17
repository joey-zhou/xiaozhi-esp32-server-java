package com.xiaozhi.file;

import com.xiaozhi.common.annotation.SignedFileUrl;
import com.xiaozhi.common.model.PageResult;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * {@link SignedFileUrl} 字段处理工具。
 * <p>
 * 供请求/响应两个方向的 Advice 复用：读边界签名、写边界剥签名，仅转换逻辑不同。
 * 自动展开 {@link PageResult} 与集合，遍历带注解的 String 字段并用给定函数替换其值。
 */
final class SignedFileUrlSupport {

    private SignedFileUrlSupport() {
    }

    /** 只递归项目自己的模型类型，不走进 JDK 与三方对象图 */
    private static final String MODEL_PACKAGE_PREFIX = "com.xiaozhi.";

    /** 每个类拆成「直接签名的 String 字段」与「需要继续下探的字段」两份 */
    private record FieldPlan(List<Field> signed, List<Field> nested) {
    }

    private static final Map<Class<?>, FieldPlan> PLAN_CACHE = new ConcurrentHashMap<>();

    /**
     * 对 data（含分页/集合/单对象）中所有 @SignedFileUrl String 字段应用 transformer。
     */
    static void apply(Object data, UnaryOperator<String> transformer) {
        apply(data, transformer, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * @param visited 按引用去重，自引用结构（如权限树）不会无限下探
     */
    private static void apply(Object data, UnaryOperator<String> transformer, Set<Object> visited) {
        if (data == null || !visited.add(data)) {
            return;
        }
        if (data instanceof PageResult<?> pageResult) {
            applyEach(pageResult.getList(), transformer, visited);
        } else if (data instanceof Collection<?> collection) {
            applyEach(collection, transformer, visited);
        } else if (data instanceof Map<?, ?> map) {
            applyEach(map.values(), transformer, visited);
        } else {
            applyObject(data, transformer, visited);
        }
    }

    private static void applyEach(Collection<?> collection, UnaryOperator<String> transformer, Set<Object> visited) {
        if (collection == null) {
            return;
        }
        for (Object item : collection) {
            apply(item, transformer, visited);
        }
    }

    private static void applyObject(Object obj, UnaryOperator<String> transformer, Set<Object> visited) {
        FieldPlan plan = PLAN_CACHE.computeIfAbsent(obj.getClass(), SignedFileUrlSupport::resolvePlan);
        for (Field field : plan.signed()) {
            try {
                if (field.get(obj) instanceof String value && !value.isEmpty()) {
                    field.set(obj, transformer.apply(value));
                }
            } catch (IllegalAccessException ignored) {
                // setAccessible 已开启，正常不会到这里
            }
        }
        for (Field field : plan.nested()) {
            try {
                apply(field.get(obj), transformer, visited);
            } catch (IllegalAccessException ignored) {
                // 同上
            }
        }
    }

    private static FieldPlan resolvePlan(Class<?> clazz) {
        List<Field> signed = new ArrayList<>();
        List<Field> nested = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.isAnnotationPresent(SignedFileUrl.class) && field.getType() == String.class) {
                    field.setAccessible(true);
                    signed.add(field);
                } else if (isNestedCandidate(field.getType())) {
                    field.setAccessible(true);
                    nested.add(field);
                }
            }
        }
        return new FieldPlan(List.copyOf(signed), List.copyOf(nested));
    }

    /** 嵌套的模型对象，以及可能装着模型对象的集合与映射 */
    private static boolean isNestedCandidate(Class<?> type) {
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }
        if (type.isPrimitive() || type.isEnum() || type.isArray()) {
            return false;
        }
        Package pkg = type.getPackage();
        return pkg != null && pkg.getName().startsWith(MODEL_PACKAGE_PREFIX);
    }
}
