package com.xiaozhi.utils;

import jakarta.persistence.criteria.*;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class BeanUtilSpecs {

    /**
     * 根据传入的示例对象 (Example Object) 自动生成 Specification。
     * 规则：
     * 1. 遍历对象所有字段。
     * 2. 如果字段值为 null，跳过。
     * 3. 如果字段是 String 且为空字符串，跳过。
     * 4. String 类型默认使用 LIKE 模糊查询 (%value%)。
     * 5. 其他类型使用 EQUALS 精确查询。
     * 6. 支持嵌套属性 (例如：order.user.name)，通过点号分隔。
     *
     * @param example 包含查询条件的对象实例
     * @param <T> 实体类型
     * @return Specification
     */
    public static <T> Specification<T> fromExample(T example) {
        if (example == null) {
            return new Specification<T>(){

                @Override
                public @Nullable Predicate toPredicate(Root<T> root, @Nullable CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                    return criteriaBuilder.conjunction();
                }
            };
        }

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Class<?> clazz = example.getClass();

            // 获取所有字段（包括父类）
            while (clazz != null && clazz != Object.class) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(example);
                        
                        // 1. 跳过 null 值
                        if (value == null) {
                            continue;
                        }

                        // 2. 跳过空字符串
                        if (value instanceof String && ((String) value).trim().isEmpty()) {
                            continue;
                        }

                        String fieldName = field.getName();
                        
                        // 处理嵌套属性 (如果字段名包含点，或者你想支持更复杂的路径，可以在这里扩展)
                        // 这里假设字段名就是数据库列名或简单的属性名
                        // 如果需要支持 user.address.city，通常需要在 DTO 中定义该字段名为 "user.address.city" 
                        // 或者使用专门的注解标记，这里演示最通用的单层级 + 简单嵌套处理
                        
                        Path<?> path = getPath(root, fieldName);

                        // 3. 根据类型构建谓词
                        if (value instanceof String) {
                            // 字符串：模糊查询 (LIKE %value%)
                            // 如果需要前缀匹配，改为 value + "%"
                            // 如果需要后缀匹配，改为 "%" + value
                            predicates.add(cb.like(path.as(String.class), "%" + value + "%"));
                        } else if (value instanceof Number || value instanceof Boolean) {
                            // 数字/布尔：精确匹配
                            predicates.add(cb.equal(path, value));
                        } else {
                            // 其他对象（如 Date, Enum 等）：精确匹配
                            // 注意：如果是关联对象且不为空，这里可能需要递归处理，
                            // 但通常查询参数是扁平的 DTO，所以直接 equals 即可
                            predicates.add(cb.equal(path, value));
                        }

                    } catch (IllegalAccessException e) {
                        // 忽略无法访问的字段
                    }
                }
                clazz = clazz.getSuperclass();
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 解析路径，支持 "user.address.city" 这种嵌套写法
     */
    private static Path<?> getPath(Root<?> root, String fieldName) {
        if (fieldName.contains(".")) {
            String[] parts = fieldName.split("\\.");
            Path<?> path = root.get(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                path = path.get(parts[i]);
            }
            return path;
        }
        return root.get(fieldName);
    }
}