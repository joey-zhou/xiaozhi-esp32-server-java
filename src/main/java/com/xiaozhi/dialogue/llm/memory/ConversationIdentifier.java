package com.xiaozhi.dialogue.llm.memory;

import lombok.Getter;

@Getter
public class ConversationIdentifier{
    private final String deviceId;
    private final Integer roleId;
    private final String sessionId;
    public ConversationIdentifier(String deviceId, Integer roleId, String sessionId){
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.sessionId = sessionId;
    }
}