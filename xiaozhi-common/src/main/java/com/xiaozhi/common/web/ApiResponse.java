package com.xiaozhi.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应结果
 *
 * @author Joey
 */
@Schema(description = "统一响应结果")
public class ApiResponse<T> {

    @Schema(description = "状态码：200-成功，500-失败", example = "200")
    private int code;

    @Schema(description = "返回消息", example = "操作成功")
    private String message;

    @Schema(description = "返回数据")
    private T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // -------------------- success --------------------

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ResultStatus.SUCCESS, "操作成功", null);
    }

    public static <T> ApiResponse<T> success(String msg) {
        return new ApiResponse<>(ResultStatus.SUCCESS, msg, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultStatus.SUCCESS, "操作成功", data);
    }

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(ResultStatus.SUCCESS, msg, data);
    }

    // -------------------- semantic helpers --------------------

    public static <T> ApiResponse<T> badRequest(String msg) {
        return new ApiResponse<>(ResultStatus.BAD_REQUEST, msg, null);
    }

    public static <T> ApiResponse<T> unauthorized(String msg) {
        return new ApiResponse<>(ResultStatus.UNAUTHORIZED, msg, null);
    }

    public static <T> ApiResponse<T> forbidden(String msg) {
        return new ApiResponse<>(ResultStatus.FORBIDDEN, msg, null);
    }

    public static <T> ApiResponse<T> notFound(String msg) {
        return new ApiResponse<>(ResultStatus.NOT_FOUND, msg, null);
    }

    public static <T> ApiResponse<T> conflict(String msg) {
        return new ApiResponse<>(ResultStatus.CONFLICT, msg, null);
    }

    public static <T> ApiResponse<T> serverError(String msg) {
        return new ApiResponse<>(ResultStatus.ERROR, msg, null);
    }

    // -------------------- error --------------------

    public static <T> ApiResponse<T> error() {
        return new ApiResponse<>(ResultStatus.ERROR, "操作失败", null);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(ResultStatus.ERROR, msg, null);
    }

    public static <T> ApiResponse<T> error(String msg, T data) {
        return new ApiResponse<>(ResultStatus.ERROR, msg, data);
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }

    public static <T> ApiResponse<T> error(int code, String msg, T data) {
        return new ApiResponse<>(code, msg, data);
    }

    // -------------------- getters --------------------

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
