package com.xiaozhi.ai.llm.memory;

import lombok.Getter;

import java.util.Objects;

@Getter
public class ConversationIdentifier {
    private final String deviceId;
    private final Integer roleId;
    private final String sessionId;

    public ConversationIdentifier(String deviceId, Integer roleId, String sessionId) {
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationIdentifier that)) return false;
        return Objects.equals(deviceId, that.deviceId)
            && Objects.equals(roleId, that.roleId)
            && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, roleId, sessionId);
    }

    @Override
    public String toString() {
        return "ConversationId{device=%s, role=%d, session=%s}"
            .formatted(deviceId, roleId, sessionId);
    }
}