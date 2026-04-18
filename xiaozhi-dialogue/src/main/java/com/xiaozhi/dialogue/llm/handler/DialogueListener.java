package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.PersonaListener;
import com.xiaozhi.dialogue.runtime.convert.DialogueTurnConverter;
import com.xiaozhi.message.service.MessageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
/**
 * PersonaListener 的 Spring 管理实现（基础设施层）。
 * 负责对话消息持久化。
 */
@Slf4j
@Component
public class DialogueListener implements PersonaListener {

    @Resource
    private MessageService messageService;

    @Resource
    private DialogueTurnConverter dialogueTurnConverter;

    @Override
    public void onDialogueTurn(DialogueTurn turn) {
        try {
            messageService.saveAll(dialogueTurnConverter.toMessages(turn));
        } catch (Exception e) {
            log.error("对话持久化失败", e);
        }
    }

    @Override
    public void onError(Throwable error) {
        log.error("LLM调用失败", error);
    }
}
