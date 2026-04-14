package com.xiaozhi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计日志注解，标记需要记录操作日志的敏感接口。
 * <p>
 * AOP 切面会在方法执行后异步将操作记录写入 sys_operation_log 表。
 * 查询接口不需要添加此注解。
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * @AuditLog(module = "设备管理", operation = "创建设备")
 * @PostMapping("/")
 * public ApiResponse<?> create(...) { ... }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 操作模块，例如"设备管理"、"用户管理"。
     */
    String module();

    /**
     * 操作描述，例如"创建设备"、"删除角色"。
     */
    String operation();
}
