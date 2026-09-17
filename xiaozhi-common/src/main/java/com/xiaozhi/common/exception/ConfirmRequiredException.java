package com.xiaozhi.common.exception;

/**
 * 操作本身合法，但有不可逆后果，需要用户看过 message 再决定是否继续。
 * <p>
 * message 直接展示给用户，要写清后果与影响范围；调用方带上各自接口约定的确认参数重发即可跳过校验。
 */
public class ConfirmRequiredException extends RuntimeException {

    public ConfirmRequiredException(String message) {
        super(message);
    }
}
