package com.xiaozhi.ai.llm;

import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 本地估算消息占用的 token 数，供对话在拿不到模型用量时判断上下文大小。
 * <p>
 * 用 DashScope SDK 自带的 Qwen 离线分词器：对 Qwen 系模型就是精确值，对其它模型的中文文本误差也在两成以内，
 * 比按字数折算稳定得多。它只是触发压缩的依据，不要求精确到个位。
 */
@Slf4j
public final class TokenEstimator {

    /** 聊天模板给每条消息加的角色标记与分隔符 */
    static final int PER_MESSAGE_OVERHEAD = 4;

    private static final Tokenizer TOKENIZER = TokenizerFactory.qwen();

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        try {
            return TOKENIZER.encodeOrdinary(text).size();
        } catch (RuntimeException e) {
            log.debug("分词失败，按字数折算: {}", e.getMessage());
            return (int) Math.ceil(text.length() * 0.8);
        }
    }

    /**
     * 一条消息的 token 数：正文加模板开销，工具调用的函数名与参数、工具返回的内容都算进去。
     */
    public static int estimate(Message message) {
        int tokens = PER_MESSAGE_OVERHEAD + estimate(message.getText());
        if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                tokens += estimate(call.name()) + estimate(call.arguments());
            }
        }
        if (message instanceof ToolResponseMessage toolResponse) {
            for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                tokens += estimate(response.name()) + estimate(response.responseData());
            }
        }
        return tokens;
    }

    public static int estimate(Collection<? extends Message> messages) {
        int tokens = 0;
        for (Message message : messages) {
            tokens += estimate(message);
        }
        return tokens;
    }
}
