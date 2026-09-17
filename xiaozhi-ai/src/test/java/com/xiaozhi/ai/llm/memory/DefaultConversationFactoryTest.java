package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.port.ConfigLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备按 ownerId + roleId 跨会话延续、Web 按 sessionId 隔离；有摘要时只加载摘要之后的消息。
 */
@ExtendWith(MockitoExtension.class)
class DefaultConversationFactoryTest {

    @Mock
    private ChatMemory chatMemory;
    @Mock
    private ChatModelFactory chatModelFactory;
    @Mock
    private ConfigLookup configLookup;
    @Mock
    private ChatModel chatModel;

    private DefaultConversationFactory factory;
    private final RoleBO role = new RoleBO();

    @BeforeEach
    void setUp() {
        factory = new DefaultConversationFactory(chatMemory, chatModelFactory, configLookup);
        ReflectionTestUtils.setField(factory, "maxMessages", 16);
        ReflectionTestUtils.setField(factory, "keepMessages", 8);
        ReflectionTestUtils.setField(factory, "contextBudgetRatio", 0.7);
        ReflectionTestUtils.setField(factory, "defaultContextLength", 32768);
        ReflectionTestUtils.setField(factory, "promptOverhead", 4000);
        role.setRoleId(1);
        role.setRoleDesc("测试角色");
        // 预算测试不建对话，不会用到模型
        lenient().when(chatModelFactory.getChatModel(role)).thenReturn(chatModel);
    }

    @Test
    void deviceConversationLoadsByOwnerAndRole() {
        when(chatMemory.find("device-1", 1, 16))
                .thenReturn(List.of(new UserMessage("你好"), new AssistantMessage("你好呀")));

        Conversation conversation = factory.initConversation("device-1", 9, role, "session-1");

        assertThat(conversation.rawMessages()).extracting(Message::getText).containsExactly("你好", "你好呀");
        verify(chatMemory, never()).findBySession(anyString(), anyInt());
    }

    @Test
    void webConversationLoadsBySessionAfterItsLastSummary() {
        Instant summarizedUntil = Instant.now().minusSeconds(60);
        when(chatMemory.findLastSummaryBySession("session-1"))
                .thenReturn(new SummaryBO().setSummary("上次聊到篮球").setLastMessageTimestamp(summarizedUntil));
        when(chatMemory.findBySession("session-1", summarizedUntil))
                .thenReturn(List.of(new UserMessage("接着聊"), new AssistantMessage("好呀")));

        Conversation conversation = factory.initSessionConversation("web:9", 9, role, "session-1");

        assertThat(conversation.rawMessages()).extracting(Message::getText).containsExactly("接着聊", "好呀");
        assertThat(conversation.messages()).extracting(Message::getText)
                .anySatisfy(text -> assertThat(text).contains("上次聊到篮球"));
        verify(chatMemory, never()).findLastSummary(anyString(), anyInt());
        verify(chatMemory, never()).find(anyString(), anyInt(), anyInt());
    }

    // 模型配置里填了上下文长度就按它的七成做预算，没填或填 0 按默认上下文长度算
    @Test
    void tokenBudgetFollowsTheModelContextLength() {
        role.setModelId(5);
        when(configLookup.getConfig(5)).thenReturn(new ConfigBO().setContextLength(131072));
        assertThat(factory.tokenBudget(role)).isEqualTo(91750);

        when(configLookup.getConfig(5)).thenReturn(new ConfigBO());
        assertThat(factory.tokenBudget(role)).isEqualTo(22938);

        role.setModelId(null);
        assertThat(factory.tokenBudget(role)).isEqualTo(22938);
    }
}
