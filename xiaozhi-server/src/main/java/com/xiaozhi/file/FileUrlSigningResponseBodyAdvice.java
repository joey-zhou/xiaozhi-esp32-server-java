package com.xiaozhi.file;

import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.Resource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * 响应体文件 URL 自动签名。
 * <p>
 * 在响应写出前扫描 {@link ApiResponse#getData()}（含分页 {@link com.xiaozhi.common.model.resp.PageResult}、
 * 集合、单对象），将标注 {@link com.xiaozhi.common.annotation.SignedFileUrl} 的 String 字段值经当前存储服务的
 * {@link StorageService#getAccessUrl(String)} 处理：云端私有桶替换为带签名的临时 URL，
 * 本地存储的受保护目录追加时效签名，两侧语义一致，此处不做分支。
 * <p>
 * presign 为本地运算无网络开销，字段元数据带缓存，列表响应亦安全。
 */
@Slf4j
@ControllerAdvice
public class FileUrlSigningResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> apiResponse) || apiResponse.getData() == null) {
            return body;
        }
        try {
            // 按值的形态选实现：库里的历史值有本地相对路径与云地址两种，
            // 拿当前生效的实现去套所有值，会让换过存储之后的历史录音签不上名、被本地拦截器判 403
            SignedFileUrlSupport.apply(apiResponse.getData(), storageServiceFactory::accessUrlOf);
        } catch (Exception e) {
            // 签名失败不应影响正常响应
            log.warn("响应文件 URL 签名处理失败", e);
        }
        return body;
    }
}
