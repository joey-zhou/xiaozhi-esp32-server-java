package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WindowConversationFactory implements ConversationFactory{

    private final ChatMemory chatMemory;

    @Autowired
    public WindowConversationFactory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public Conversation initConversation(SysDevice device, SysRole role, String sessionId) {
        Conversation conversation = MessageWindowConversation.builder().chatMemory(chatMemory)
                .maxMessages(16)
                .role(role)
                .device(device)
                .sessionId(sessionId)
                .build();
        return conversation;
    }
}
