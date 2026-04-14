package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * STT 语音识别完成事件。
 * 在 DialogueService.startStt() 中，STT 识别出用户文本后同步发布。
 *
 * <p><b>注意：此事件在虚拟线程中同步处理，监听器不应引入阻塞操作或延迟。</b>
 *
 * <p>扩展点（当前无监听器）：
 * <ul>
 *   <li>情感分析日志：记录用户情感标签用于对话质量分析</li>
 *   <li>敏感词过滤：在 LLM 调用前对用户文本进行安全过滤</li>
 * </ul>
 */
public class SpeechRecognizedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String text;
    private final String emotion;

    public SpeechRecognizedEvent(Object source, String sessionId, String text, String emotion) {
        super(source);
        this.sessionId = sessionId;
        this.text = text;
        this.emotion = emotion;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getText() {
        return text;
    }

    /**
     * 用户语音中检测到的情感标签，可能为 null。
     */
    public String getEmotion() {
        return emotion;
    }
}
