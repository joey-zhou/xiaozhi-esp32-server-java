package com.xiaozhi.common.exception;

/**
 * 用于表达已知的业务操作失败，但不属于参数错误或资源不存在。
 */
public class OperationFailedException extends RuntimeException {

    public OperationFailedException(String message) {
        super(message);
    }

    public OperationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
