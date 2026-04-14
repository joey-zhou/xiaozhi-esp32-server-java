package com.xiaozhi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明当前接口需要校验某个资源是否归属当前登录用户。
 * <p>
 * {@code id} 使用 SpEL 表达式从方法参数中提取资源标识，例如：
 * <pre>
 * {@code
 * @CheckOwner(resource = "role", id = "#roleId")
 * @CheckOwner(resource = "device", id = "#req.deviceId")
 * }
 * </pre>
 * 也支持返回数组或集合，切面会逐项校验归属。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CheckOwners.class)
public @interface CheckOwner {

    /**
     * 资源类型，对应后端注册的 ownership checker 名称。
     */
    String resource();

    /**
     * 资源 ID 的 SpEL 表达式，例如 {@code #roleId}、{@code #req.deviceId}。
     */
    String id();

    /**
     * 管理员是否可以绕过该资源校验。
     */
    boolean adminBypass() default true;
}
