package com.xiaozhi.dialogue.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 对话已被中止（事实）：停播、历史收尾都做完之后发出。要打断对话的一方直调 DialogueService.abortDialogue，
 * 不发这个事件
 */
@Getter
public class ChatAbortedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String deviceId;
    private final AbortReason reason;

    public ChatAbortedEvent(Object source, String sessionId, String deviceId, AbortReason reason) {
        super(source);
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.reason = reason;
    }
}
