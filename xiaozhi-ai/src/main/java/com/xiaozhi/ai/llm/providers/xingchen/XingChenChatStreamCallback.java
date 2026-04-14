package com.xiaozhi.ai.llm.providers.xingchen;

/**
 * 星辰Agent流式回调接口
 * 基于文档: https://www.xfyun.cn/doc/spark/Agent04-API%E6%8E%A5%E5%85%A5.html
 */
public interface XingChenChatStreamCallback {

    /**
     * 收到普通消息
     * @param event 响应事件
     */
    default void onMessage(XingChenResponse event) {
    }

    /**
     * 消息流结束
     * @param event 响应事件 (可能为null)
     */
    default void onMessageEnd(XingChenResponse event) {
    }

    /**
     * 收到工具调用事件
     * @param event 响应事件 (包含 event_data)
     */
    default void onFunctionCall(XingChenResponse event) {
    }

    /**
     * 发生错误
     * @param event 错误响应 (code != 0)
     */
    default void onError(XingChenResponse event) {
    }

    /**
     * 发生异常
     * @param throwable 异常对象
     */
    default void onException(Throwable throwable) {
    }
}
