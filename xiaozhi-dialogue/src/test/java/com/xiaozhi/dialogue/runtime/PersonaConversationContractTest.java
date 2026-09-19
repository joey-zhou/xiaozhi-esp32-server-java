package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persona 持有的 Conversation 恒非空，调用方不必判空。
 * 这条约束靠 build 时抛错来兜底：漏给的构造路径当场暴露，而不是等到某次 flush 才 NPE。
 */
class PersonaConversationContractTest {

    private static Conversation conversation() {
        return Conversation.of("device-1", 1, "session-1", "测试角色", 1);
    }

    @Test
    void buildingWithoutConversationFailsFast() {
        assertThatThrownBy(() -> Persona.builder().sessionId("session-1").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conversation");
    }

    @Test
    void conversationIsReadableOnceBuilt() {
        Persona persona = Persona.builder().sessionId("session-1").conversation(conversation()).build();

        assertThat(persona.getConversation()).isNotNull();
    }
}
