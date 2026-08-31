package com.xiaozhi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记响应 DTO 中存放对象存储文件路径的字段。
 * <p>
 * 由 {@code FileUrlSigningResponseBodyAdvice} 在响应写出前统一处理：
 * 云存储私有桶下会将字段值替换为带签名的临时访问 URL，本地存储原样返回。
 * 仅作用于 String 类型字段。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SignedFileUrl {
}
