package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.PersonaListener;
import com.xiaozhi.message.service.MessageService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PersonaListener 的 Spring 管理实现（基础设施层）。
 * 负责对话消息持久化。
 */
@Component
public class DialogueListener implements PersonaListener {

    private static final Logger logger = LoggerFactory.getLogger(DialogueListener.class);

    @Resource
    private MessageService messageService;

    @Override
    public void onDialogueTurn(DialogueTurn turn) {
        try {
            messageService.saveAll(turn.toMessages());
        } catch (Exception e) {
            logger.error("对话持久化失败", e);
        }
    }

    @Override
    public void onError(Throwable error) {
        logger.error("LLM调用失败", error);
    }
}
