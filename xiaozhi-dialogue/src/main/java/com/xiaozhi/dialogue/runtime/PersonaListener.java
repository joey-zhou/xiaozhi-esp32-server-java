package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;

import java.time.Instant;

/**
 * Persona 生命周期回调接口（领域层，无框架依赖）。
 * <p>
 * 将 Persona 与基础设施（消息持久化、监控统计）解耦：
 * Persona 只通过此接口通知外部"发生了什么"，由 Spring 管理的实现类决定"做什么"。
 * <p>
 * 分布式友好：将来需要集群广播时，只需让实现类内部桥接到 Redis/Kafka，Persona 完全不用改。
 * <p>
 * 注：STT 语音识别和工具调用发生在 Persona 外部（分别在 DialogueService 和 Spring AI 框架内），
 * 因此通过 Spring Event（SpeechRecognizedEvent、ToolCallCompletedEvent）而非此接口通知。
 *
 * @see com.xiaozhi.dialogue.llm.handler.DialogueListener
 */
public interface PersonaListener {

    /**
     * 一轮 Conversation 完成后回调。
     * 实现类负责持久化消息、记录 LLM 调用成功等。
     *
     * @param turn 本轮对话的完整信息
     */
    void onDialogueTurn(DialogueTurn turn);

    /**
     * 已落库的一轮在播放途中被打断：助手消息截到用户听到的位置。
     * spokenText 为空表示一个字都没播出，实现方应把该条助手消息删掉。
     *
     * @param conversation              所属会话
     * @param assistantMessageCreatedAt 落库时的助手消息创建时间，用于定位记录
     * @param spokenText                用户实际听到的文本
     */
    void onDialogueTurnTruncated(Conversation conversation, Instant assistantMessageCreatedAt, String spokenText);

    /**
     * LLM 调用出错时回调。
     * 实现类负责记录 LLM 调用失败、日志等。
     *
     * @param error 错误信息
     */
    void onError(Throwable error);
}
