package com.xiaozhi.server.web.chat.convert;

import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/** record 访问器按名映射到 Resp，漏映射会让前端拿到 type 或 text 为 null 的 Token。 */
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
}
