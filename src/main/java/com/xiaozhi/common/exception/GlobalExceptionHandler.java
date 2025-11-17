package com.xiaozhi.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import com.xiaozhi.common.web.ResultMessage;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 * 
 * @author Joey
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 用户名不存在异常
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage handleUsernameNotFoundException(UsernameNotFoundException e, WebRequest request) {
        logger.warn("用户名不存在异常: {}", e.getMessage(), e);
        return ResultMessage.error("用户名不存在");
    }

    /**
     * 用户密码不匹配异常
     */
    @ExceptionHandler(UserPasswordNotMatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage handleUserPasswordNotMatchException(UserPasswordNotMatchException e, WebRequest request) {
        logger.warn("用户密码不匹配异常: {}", e.getMessage(), e);
        return ResultMessage.error("用户密码不正确");
    }

    /**
     * 权限不足异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResultMessage handleUnauthorizedException(UnauthorizedException e, WebRequest request) {
        logger.warn("权限不足: {}", e.getMessage());
        return ResultMessage.error(HttpStatus.FORBIDDEN.value(), e.getMessage());
    }

    /**
     * 资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResultMessage handleResourceNotFoundException(ResourceNotFoundException e, WebRequest request) {
        logger.warn("资源不存在: {}", e.getMessage());
        return ResultMessage.error(HttpStatus.NOT_FOUND.value(), e.getMessage());
    }

    /**
     * 静态资源找不到异常
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResultMessage handleNoResourceFoundException(NoResourceFoundException e, WebRequest request) {
        logger.warn("静态资源找不到: {}", e.getResourcePath());
        return ResultMessage.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在");
    }

    /**
     * 请求路径不存在异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResultMessage handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        logger.warn("请求路径不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return ResultMessage.error(HttpStatus.NOT_FOUND.value(), "请求的接口不存在");
    }

    /**
     * 请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResultMessage handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.warn("请求方法不支持: {} {}, 支持的方法: {}", e.getMethod(), request.getRequestURI(), e.getSupportedHttpMethods());
        return ResultMessage.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不支持");
    }

    /**
     * 异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    public ResultMessage handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e, WebRequest request) {
        logger.warn("异步请求超时: {}", request.getDescription(false));
        return ResultMessage.error(HttpStatus.REQUEST_TIMEOUT.value(), "请求超时，请稍后重试");
    }

    /**
     * 业务异常处理
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage handleRuntimeException(RuntimeException e, WebRequest request) {
        logger.error("业务异常: {}", e.getMessage(), e);
        return ResultMessage.error("操作失败：" + e.getMessage());
    }

    /**
     * 系统异常 - 作为最后的兜底处理
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResultMessage handleException(Exception e, WebRequest request) {
        logger.error("系统异常: {}", e.getMessage(), e);
        return ResultMessage.error("服务器错误，请联系管理员");
    }
}