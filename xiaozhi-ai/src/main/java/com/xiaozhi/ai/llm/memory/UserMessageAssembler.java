package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.MessageMetadataBO;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 装配带元数据前缀的 UserMessage。
 *
 * <p>输出格式（F1 约定）：
 * <pre>
 * [2026-04-18T12:35:42][说话人:张三][neutral] 西游记主角是谁?
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>元数据以 UserMessage 前缀形式送 LLM（非 System Prompt、非 Tool Call），
 *       System Prompt 保持稳定利于前缀 KV cache；每条历史消息自带时空属性。</li>
 *   <li>字段顺序固定：时间戳 → 说话人 → 情绪；任一缺省跳过对应方括号。</li>
 *   <li>时间戳无 key；说话人带 <code>说话人:</code>；情绪无 key（沿用 <code>[neutral]</code> 约定）。</li>
 * </ul>
 *
 * <p>System Prompt 里需有解读规则，见
 * {@link Conversation#roleSystemMessage(ConversationContext)}。
 */
public final class UserMessageAssembler {

    /**
     * 严格到秒的 ISO_LOCAL_DATE_TIME，确保秒位始终输出，便于模型解析。
     */
    public static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private UserMessageAssembler() {}

    /**
     * 高阶：根据 UserMessage.metadata 里的时间戳 / {@link MessageMetadataBO} 装配带前缀的新 UserMessage。
     * <ul>
     *   <li>非 UserMessage：原样返回。</li>
     *   <li>三项元数据全缺：原样返回（兼容无 metadata 的历史老消息）。</li>
     * </ul>
     */
    public static Message assemble(Message m) {
        if (!(m instanceof UserMessage um)) {
            return m;
        }
        Map<String, Object> meta = um.getMetadata();
        Instant timestamp = meta != null && meta.get(ChatMemory.TIME_MILLIS_KEY) instanceof Instant i ? i : null;
        MessageMetadataBO bo = meta != null && meta.get(MessageMetadataBO.METADATA_KEY) instanceof MessageMetadataBO b
                ? b : null;
        String emotion = bo != null ? bo.getEmotion() : null;
        if (timestamp == null && !StringUtils.hasText(emotion)) {
            return um;
        }
        return UserMessage.builder()
                .text(assemble(um.getText(), timestamp, emotion))
                .metadata(meta == null ? new HashMap<>() : new HashMap<>(meta))
                .build();
    }

    /**
     * 低阶：纯字符串拼接，外部一般不直接用。
     */
    public static String assemble(String text, Instant timestamp, String emotion) {
        StringBuilder sb = new StringBuilder();
        if (timestamp != null) {
            LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
            sb.append('[').append(ldt.format(TIMESTAMP_FORMATTER)).append(']');
        }
        if (StringUtils.hasText(emotion)) {
            sb.append('[').append(emotion).append(']');
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(text == null ? "" : text);
        return sb.toString();
    }
}
