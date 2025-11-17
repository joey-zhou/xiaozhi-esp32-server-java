package com.xiaozhi.common.web;

import org.springframework.util.ObjectUtils;

import java.util.HashMap;

/**
 * 处理结果封装
 * 
 * @author Joey
 */
public class ResultMessage extends HashMap<String, Object> {

    /** 状态码 */
    public static final String CODE_TAG = "code";

    /** 返回内容 */
    public static final String MSG_TAG = "message";

    /** 数据对象 */
    public static final String DATA_TAG = "data";

    /**
     * 初始化一个新创建的 ResultMessage 对象，使其表示一个空消息。
     */
    public ResultMessage() {
    }

    /**
     * 初始化一个新创建的 ResultMessage 对象
     * 
     * @param code 状态码
     * @param msg  返回内容
     */
    public ResultMessage(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    /**
     * 初始化一个新创建的 ResultMessage 对象
     * 
     * @param code 状态码
     * @param msg  返回内容
     * @param data 数据对象
     */
    public ResultMessage(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (!ObjectUtils.isEmpty(data)) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 返回成功消息
     * 
     * @return 成功消息
     */
    public static ResultMessage success() {
        return ResultMessage.success("操作成功");
    }

    /**
     * 返回成功数据
     * 
     * @return 成功消息
     */
    public static ResultMessage success(Object data) {
        return ResultMessage.success("操作成功", data);
    }

    /**
     * 返回成功消息
     * 
     * @param msg 返回内容
     * @return 成功消息
     */
    public static ResultMessage success(String msg) {
        return ResultMessage.success(msg, null);
    }

    /**
     * 返回成功消息
     * 
     * @param msg  返回内容
     * @param data 数据对象
     * @return 成功消息
     */
    public static ResultMessage success(String msg, Object data) {
        return new ResultMessage(ResultStatus.SUCCESS, msg, data);
    }

    /**
     * 返回错误消息
     * 
     * @return
     */
    public static ResultMessage error() {
        return ResultMessage.error("操作失败");
    }

    /**
     * 返回错误消息
     * 
     * @param msg 返回内容
     * @return 警告消息
     */
    public static ResultMessage error(String msg) {
        return ResultMessage.error(msg, null);
    }

    /**
     * 返回错误消息
     * 
     * @param msg  返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static ResultMessage error(String msg, Object data) {
        return new ResultMessage(ResultStatus.ERROR, msg, data);
    }

    /**
     * 返回错误消息
     * 
     * @param code 状态码
     * @param msg  返回内容
     * @return 警告消息
     */
    public static ResultMessage error(int code, String msg) {
        return new ResultMessage(code, msg, null);
    }

    /**
     * 返回错误消息
     * 
     * @param code 状态码
     * @param msg  返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static ResultMessage error(int code, String msg, Object data) {
        return new ResultMessage(code, msg, data);
    }

    public int getCode() {
        return (int) super.get(CODE_TAG);
    }

    public String getMessage() {
        return (String) super.get(MSG_TAG);
    }

    public Object getData() {
        return super.get(DATA_TAG);
    }
}