package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

/**
 * 添加智能体请求参数
 *
 * @author Joey
 */
@Data
@Schema(description = "添加智能体请求参数")
public class AgentAddParam {

    @Schema(description = "智能体名称", example = "客服助手", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "智能体名称不能为空")
    private String agentName;

    @Schema(description = "平台智能体ID（Coze或Dify的BotId）", example = "73428***44858007", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "平台智能体ID不能为空")
    private String botId;

    @Schema(description = "智能体描述", example = "专业的客户服务智能助手")
    private String agentDesc;

    @Schema(description = "图标URL", example = "https://example.com/icon.png")
    private String iconUrl;

    @Schema(description = "发布时间", example = "2024-01-01 00:00:00")
    private Date publishTime;

    @Schema(description = "设备ID", example = "ESP32_001")
    private String deviceId;

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;

    @Schema(description = "配置名称", example = "Coze智能体")
    private String configName;

    @Schema(description = "配置描述", example = "基于Coze的智能对话配置")
    private String configDesc;

    @Schema(description = "配置类型", example = "llm", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"llm", "stt", "tts"})
    @NotBlank(message = "配置类型不能为空")
    private String configType;

    @Schema(description = "模型类型", example = "chat", allowableValues = {"chat", "vision", "intent", "embedding", "director"})
    private String modelType;

    @Schema(description = "服务提供商", example = "coze", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"openai", "qwen", "coze", "dify", "tencent", "aliyun"})
    @NotBlank(message = "服务提供商不能为空")
    private String provider;

    @Schema(description = "服务提供商分配的AppId", example = "app_123456")
    private String appId;

    @Schema(description = "服务提供商分配的ApiKey", example = "sk-xxxxx")
    private String apiKey;

    @Schema(description = "服务提供商分配的ApiSecret", example = "secret_xxxxx")
    private String apiSecret;

    @Schema(description = "服务提供商分配的Access Key", example = "ak_xxxxx")
    private String ak;

    @Schema(description = "服务提供商分配的Secret Key", example = "sk_xxxxx")
    private String sk;

    @Schema(description = "服务提供商的API地址", example = "https://api.coze.cn")
    private String apiUrl;

    @Schema(description = "服务提供商状态", example = "1", allowableValues = {"0", "1"})
    private String state;

    @Schema(description = "是否作为默认配置", example = "0", allowableValues = {"0", "1"})
    private String isDefault;
}
