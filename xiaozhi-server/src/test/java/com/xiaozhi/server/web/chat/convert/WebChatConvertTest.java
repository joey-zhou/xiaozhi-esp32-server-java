package com.xiaozhi.server.web.chat.convert;

import com.xiaozhi.ai.llm.memory.ToolChainPair;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.MessageAttachmentBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * record 访问器按名映射到 Resp，漏映射会让前端拿到 type 或 text 为 null 的 Token；
 * 一轮对话落库的两条消息归属 web:<userId>、来源 web，时间用调用方给的那份。
 */
class WebChatConvertTest {

    private final WebChatConvert convert = Mappers.getMapper(WebChatConvert.class);

    @Test
    void toRespCarriesTypeAndText() {
        ChatTokenResp thinking = convert.toResp(ChatToken.thinking("先想一想"));
        ChatTokenResp content = convert.toResp(ChatToken.content("你好"));

        assertThat(thinking.getType()).isEqualTo(ChatToken.TYPE_THINKING);
        assertThat(thinking.getText()).isEqualTo("先想一想");
        assertThat(content.getType()).isEqualTo(ChatToken.TYPE_CONTENT);
        assertThat(content.getText()).isEqualTo("你好");
    }

    @Test
    void toMessagesBuildsUserThenAssistantOfTheWebOwner() {
        LocalDateTime asked = LocalDateTime.of(2026, 9, 15, 7, 33, 0);
        LocalDateTime answered = LocalDateTime.of(2026, 9, 15, 7, 33, 2);

        List<MessageBO> messages = convert.toMessages("s-1", 9, 1, "在吗", List.of(), asked, List.of(), "在的", answered);

        assertThat(messages).extracting(MessageBO::getSender)
            .containsExactly(MessageBO.SENDER_USER, MessageBO.SENDER_ASSISTANT);
        assertThat(messages).extracting(MessageBO::getMessage).containsExactly("在吗", "在的");
        assertThat(messages).extracting(MessageBO::getCreateTime).containsExactly(asked, answered);
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getSessionId()).isEqualTo("s-1");
            assertThat(message.getUserId()).isEqualTo(9);
            assertThat(message.getRoleId()).isEqualTo(1);
            assertThat(message.getDeviceId()).isEqualTo("web:9");
            assertThat(message.getSource()).isEqualTo(MessageBO.SOURCE_WEB);
            assertThat(message.getMessageType()).isEqualTo(MessageBO.MESSAGE_TYPE_NORMAL);
        });
    }

    /** 看图调用链落在用户消息与助手回复之间，挂用户消息的时间，续聊时重载得回来 */
    @Test
    void toolChainsGoBetweenTheQuestionAndTheAnswer() {
        LocalDateTime asked = LocalDateTime.of(2026, 9, 15, 7, 33, 0);
        LocalDateTime answered = LocalDateTime.of(2026, 9, 15, 7, 33, 2);
        ToolChainPair chain = viewedImage("一朵红色的月季");

        List<MessageBO> messages = convert.toMessages("s-1", 9, 1, "这是什么花", List.of(), asked, List.of(chain), "是月季", answered);

        assertThat(messages).extracting(MessageBO::getMessageType).containsExactly(MessageBO.MESSAGE_TYPE_NORMAL,
            MessageBO.MESSAGE_TYPE_TOOL_CALL, MessageBO.MESSAGE_TYPE_TOOL_RESPONSE, MessageBO.MESSAGE_TYPE_NORMAL);
        assertThat(messages.get(2).getMessage()).isEqualTo("一朵红色的月季");
        assertThat(messages.get(1).getCreateTime()).isEqualTo(asked);
        assertThat(messages).allSatisfy(message -> assertThat(message.getSource()).isEqualTo(MessageBO.SOURCE_WEB));
    }

    /** 附件记在用户消息上，没带附件的不挂 metadata */
    @Test
    void attachmentsAreKeptOnTheUserMessage() {
        LocalDateTime asked = LocalDateTime.of(2026, 9, 15, 7, 33, 0);
        MessageAttachmentBO photo = new MessageAttachmentBO(MessageAttachmentBO.KIND_IMAGE, "花.png", "uploads/chat/a.png");

        List<MessageBO> messages = convert.toMessages("s-1", 9, 1, "这是什么花", List.of(photo), asked, List.of(),
            "是月季", asked.plusSeconds(3));

        assertThat(messages.getFirst().getMetadata().getAttachments()).containsExactly(photo);
        assertThat(messages.getLast().getMetadata()).isNull();
        assertThat(convert.toMessages("s-1", 9, 1, "在吗", List.of(), asked, List.of(), "在的", asked)
            .getFirst().getMetadata()).isNull();
    }

    private static ToolChainPair viewedImage(String description) {
        AssistantMessage call = AssistantMessage.builder()
            .content("查看用户发来的图片")
            .properties(Map.of())
            .toolCalls(List.of(new AssistantMessage.ToolCall("viewImage", "function", "viewImage", "{}")))
            .build();
        ToolResponseMessage response = ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse("viewImage", "viewImage", description)))
            .build();
        return new ToolChainPair(call, response);
    }
}
