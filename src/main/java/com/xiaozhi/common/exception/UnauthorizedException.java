package com.xiaozhi.common.exception;

/**
 * 权限不足异常
 * 当用户尝试操作不属于自己的资源时抛出
 * 
 * @author Joey
 */
public class UnauthorizedException extends RuntimeException {
    
    public UnauthorizedException(String message) {
        super(message);
    }
    
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}

