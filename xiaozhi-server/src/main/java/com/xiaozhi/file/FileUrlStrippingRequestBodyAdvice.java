package com.xiaozhi.file;

import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.Resource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;

/**
 * 请求体文件 URL 入库前剥签名。
 * <p>
 * 前端上传后拿到的是带签名的临时 URL，提交保存时若直接入库会写入一串会过期的签名参数。
 * 本 Advice 在请求体反序列化后、进入 Controller 前，将标注
 * {@link com.xiaozhi.common.annotation.SignedFileUrl} 的 String 字段经
 * {@link StorageService#stripSignature(String)} 还原为裸地址，保证数据库始终存干净的 URL。
 */
@Slf4j
@ControllerAdvice
public class FileUrlStrippingRequestBodyAdvice implements RequestBodyAdvice {

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        if (body != null) {
            try {
                StorageService storageService = storageServiceFactory.getStorageService();
                SignedFileUrlSupport.apply(body, storageService::stripSignature);
            } catch (Exception e) {
                // 剥签名失败不应阻断请求
                log.warn("请求体文件 URL 剥签名处理失败", e);
            }
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
