package com.xiaozhi.server.web.chat.convert;

import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

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

        List<MessageBO> messages = convert.toMessages("s-1", 9, 1, "在吗", asked, "在的", answered);

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
}
