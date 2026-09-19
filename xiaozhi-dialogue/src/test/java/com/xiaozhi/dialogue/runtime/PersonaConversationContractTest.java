package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

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

    /** conversationMessages 取的是裸消息快照：没有角色系统提示词也没有摘要，文本未拼元数据前缀 */
    @Test
    void conversationMessagesAreRawTurnsWithoutThePrompt() {
        Conversation conversation = conversation();
        Persona persona = Persona.builder().sessionId("session-1").conversation(conversation).build();
        assertThat(persona.conversationMessages()).isEmpty();

        conversation.add(new UserMessage("讲个故事"));

        List<Message> messages = persona.conversationMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.getFirst().getText()).isEqualTo("讲个故事");
    }

    /** 快照是副本：调用方遍历期间会话继续追加，不会撞 ConcurrentModificationException */
    @Test
    void snapshotDoesNotSeeLaterAppends() {
        Conversation conversation = conversation();
        Persona persona = Persona.builder().sessionId("session-1").conversation(conversation).build();
        conversation.add(new UserMessage("第一句"));

        List<Message> snapshot = persona.conversationMessages();
        conversation.add(new UserMessage("第二句"));

        assertThat(snapshot).hasSize(1);
        assertThat(persona.conversationMessages()).hasSize(2);
    }
}
