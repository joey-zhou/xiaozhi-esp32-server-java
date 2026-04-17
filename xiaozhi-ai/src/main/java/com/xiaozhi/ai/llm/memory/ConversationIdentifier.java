package com.xiaozhi.ai.llm.memory;

import lombok.Getter;

import java.util.Objects;

@Getter
public class ConversationIdentifier {
    private final String ownerId;
    private final Integer roleId;
    private final String sessionId;

    public ConversationIdentifier(String ownerId, Integer roleId, String sessionId) {
        this.ownerId = ownerId;
        this.roleId = roleId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationIdentifier that)) return false;
        return Objects.equals(ownerId, that.ownerId)
            && Objects.equals(roleId, that.roleId)
            && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, roleId, sessionId);
    }

    @Override
    public String toString() {
        return "ConversationId{owner=%s, role=%d, session=%s}"
            .formatted(ownerId, roleId, sessionId);
    }
}