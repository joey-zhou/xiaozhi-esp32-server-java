package com.xiaozhi.common.exception;

/**
 * 资源不存在异常
 * 当请求的资源（剧本、脚本、角色等）不存在时抛出
 * 
 * @author Joey
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

