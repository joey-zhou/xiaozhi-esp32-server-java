package com.xiaozhi.common.model.bo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserMessage 附加元数据，结构化存储于 sys_message.metadata (JSON 列)。
 *
 * <p>设计：与 message 文本列分离，保证：
 * <ul>
 *   <li>message 列仅存用户裸文本，前端直接展示无需剥离</li>
 *   <li>LLM 读取时由 Conversation 层做"裸文本 + metadata → 带前缀文本"投影，前缀 KV cache 友好</li>
 *   <li>未来扩展附加字段（如 asrConfidence、speakerDirection 等）只需加本类字段，不改 DB</li>
 * </ul>
 *
 * <p>为空字段不序列化，JSON 更紧凑；反序列化时缺省字段默认 null。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageMetadataBO {

    /**
     * Spring AI {@code UserMessage.metadata} Map 里存放本对象用的 key。
     * Write/Read 路径统一通过该 key 在 UserMessage 上附带元数据：
     * <pre>
     *   userMessage.getMetadata().put(METADATA_KEY, metadataBO);
     *   MessageMetadataBO m = (MessageMetadataBO) userMessage.getMetadata().get(METADATA_KEY);
     * </pre>
     */
    public static final String METADATA_KEY = "userMessageMetadata";

    /**
     * 语音情感识别标签（neutral/happy/sad/angry/...），仅支持情感识别的 STT 有值。
     */
    private String emotion;

    /**
     * 情绪置信度 [0,1]，仅情感识别时有值。
     */
    private Double emotionScore;

    /**
     * 情绪强度（弱/中/强 等），仅情感识别时有值。
     */
    private String emotionDegree;
}
