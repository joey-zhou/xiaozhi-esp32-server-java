package com.xiaozhi.dialogue.llm.handler;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.PersonaListener;
import com.xiaozhi.dialogue.runtime.convert.DialogueTurnConverter;
import com.xiaozhi.message.service.MessageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
    public void onDialogueTurnTruncated(Conversation conversation, Instant assistantMessageCreatedAt, String spokenText) {
        try {
            messageService.truncateAssistant(conversation.getOwnerId(), conversation.getRoleId(),
                    LocalDateTime.ofInstant(assistantMessageCreatedAt, ZoneId.systemDefault()), spokenText);
        } catch (Exception e) {
            log.error("截断被打断的助手消息失败", e);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (error instanceof WebClientResponseException webErr) {
            log.error("LLM调用失败 status={} body={}",
                    webErr.getStatusCode(), webErr.getResponseBodyAsString(), error);
        } else {
            log.error("LLM调用失败", error);
        }
    }
}
