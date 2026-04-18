package com.xiaozhi.server.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;
/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleUsernameNotFoundException(UsernameNotFoundException e, WebRequest request) {
        log.warn("用户名不存在异常: {}", e.getMessage(), e);
        return ApiResponse.badRequest("用户名不存在");
    }

    @ExceptionHandler(UserPasswordNotMatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleUserPasswordNotMatchException(UserPasswordNotMatchException e, WebRequest request) {
        log.warn("用户密码不匹配异常: {}", e.getMessage(), e);
        return ApiResponse.badRequest("用户密码不正确");
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleUnauthorizedException(UnauthorizedException e, WebRequest request) {
        log.warn("权限不足: {}", e.getMessage());
        return ApiResponse.forbidden(e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<?> handleNotLoginException(NotLoginException e, WebRequest request) {
        return ApiResponse.unauthorized("登录已过期，请重新登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleNotPermissionException(NotPermissionException e, WebRequest request) {
        log.warn("权限不足: {}", e.getMessage());
        return ApiResponse.forbidden("权限不足");
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleNotRoleException(NotRoleException e, WebRequest request) {
        log.warn("角色权限不足: {}", e.getMessage());
        return ApiResponse.forbidden("角色权限不足");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleResourceNotFoundException(ResourceNotFoundException e, WebRequest request) {
        log.warn("资源不存在: {}", e.getMessage());
        return ApiResponse.notFound(e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNoResourceFoundException(NoResourceFoundException e, WebRequest request) {
        log.warn("静态资源找不到: {}", e.getResourcePath());
        return ApiResponse.notFound("请求的资源不存在");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("请求路径不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return ApiResponse.notFound("请求的接口不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<?> handleHttpRequestMethodNotSupportedException(
        HttpRequestMethodNotSupportedException e,
        HttpServletRequest request
    ) {
        log.warn("请求方法不支持: {} {}, 支持的方法: {}", e.getMethod(), request.getRequestURI(), e.getSupportedHttpMethods());
        return ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不支持");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    public ApiResponse<?> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e, WebRequest request) {
        log.warn("异步请求超时: {}", request.getDescription(false));
        return ApiResponse.error(HttpStatus.REQUEST_TIMEOUT.value(), "请求超时，请稍后重试");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleBindException(Exception e) {
        BindingResult bindingResult = e instanceof MethodArgumentNotValidException methodArgumentNotValidException
            ? methodArgumentNotValidException.getBindingResult()
            : ((BindException) e).getBindingResult();
        String message = extractBindingMessage(bindingResult, "请求参数不合法");
        log.warn("请求参数校验失败: {}", message);
        return ApiResponse.badRequest(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .map(violation -> violation.getMessage())
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("请求参数不合法");
        log.warn("约束校验失败: {}", message);
        return ApiResponse.badRequest(message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("请求缺少参数: {}", e.getParameterName());
        return ApiResponse.badRequest("缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getName(), e);
        return ApiResponse.badRequest("参数类型不合法: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ApiResponse.badRequest("请求体格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalArgumentException(IllegalArgumentException e, WebRequest request) {
        log.warn("参数错误: {}", e.getMessage(), e);
        return ApiResponse.badRequest(defaultMessage(e.getMessage(), "请求参数不合法"));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleIllegalStateException(IllegalStateException e, WebRequest request) {
        log.warn("业务状态冲突: {}", e.getMessage(), e);
        return ApiResponse.conflict(defaultMessage(e.getMessage(), "当前状态不允许此操作"));
    }

    @ExceptionHandler(OperationFailedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleOperationFailedException(OperationFailedException e, WebRequest request) {
        log.error("业务操作失败: {}", e.getMessage(), e);
        return ApiResponse.serverError(defaultMessage(e.getMessage(), "操作失败，请稍后重试"));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleRuntimeException(RuntimeException e, WebRequest request) {
        log.error("业务异常: {}", e.getMessage(), e);
        return ApiResponse.serverError("服务器错误，请联系管理员");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception e, WebRequest request) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ApiResponse.serverError("服务器错误，请联系管理员");
    }

    private String extractBindingMessage(BindingResult bindingResult, String fallback) {
        if (bindingResult == null) {
            return fallback;
        }
        FieldError fieldError = bindingResult.getFieldError();
        if (fieldError != null && StringUtils.hasText(fieldError.getDefaultMessage())) {
            return fieldError.getDefaultMessage();
        }
        return fallback;
    }

    private String defaultMessage(String message, String fallback) {
        return StringUtils.hasText(message) ? message : fallback;
    }
}
