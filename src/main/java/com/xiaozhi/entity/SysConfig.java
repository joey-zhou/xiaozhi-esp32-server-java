package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * LLM\STT\TTS配置
 * 
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "配置信息")
@Entity
@Table(name = "sys_config")
public class SysConfig extends Base<SysConfig> {

    @Getter
    public enum ModelType {
        chat("chat"),
        vision("vision"),
        intent("intent"),
        embedding("embedding"),
        director("director");

        @JsonValue
        private final String value;

        ModelType(String value) {
            this.value = value;
        }
    }

    /**
     * 配置 ID，主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "配置ID")
    private Integer configId;

    /**
     * 用户 ID
     */
    @Column(nullable = false)
    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "设备ID")
    @Transient
    private String deviceId;

    @Schema(description = "角色ID")
    @Transient
    private Integer roleId;

    /**
     * 配置名称
     */
    @Column(length = 50)
    @Schema(description = "配置名称")
    private String configName;

    /**
     * 配置描述
     */
    @Column(columnDefinition = "text")
    @Schema(description = "配置描述")
    private String configDesc;

    /**
     * 配置类型（llm\stt\tts）
     */
    @Column(nullable = false, length = 30)
    @Schema(description = "配置类型（llm\\stt\\tts）")
    private String configType;

    /**
     * 模型类型（chat\vision\intent\embedding）
     */
    @Column(length = 30)
    @Schema(description = "模型类型（chat\\vision\\intent\\embedding）")
    private String modelType;

    /**
     * 服务提供商 (openai\quen\vosk\aliyun\tencent等)
     */
    @Column(nullable = false, length = 30)
    @Schema(description = "服务提供商 (openai\\quen\\vosk\\aliyun\\tencent等)")
    private String provider;

    /**
     * APP ID
     */
    @Column(length = 100)
    @Schema(description = "服务提供商分配的AppId")
    private String appId;

    /**
     * API Key
     */
    @Column(columnDefinition = "text")
    @Schema(description = "服务提供商分配的ApiKey")
    private String apiKey;

    /**
     * API Secret
     */
    @Column(length = 255)
    @Schema(description = "服务提供商分配的ApiSecret")
    private String apiSecret;

    /**
     * Access Key
     */
    @Column(length = 255)
    @Schema(description = "服务提供商分配的Access Key")
    private String ak;

    /**
     * Secret Key
     */
    @Column(columnDefinition = "text")
    @Schema(description = "服务提供商分配的Secret Key")
    private String sk;

    /**
     * API 地址
     */
    @Column(length = 255)
    @Schema(description = "服务提供商的API地址")
    private String apiUrl;

    /**
     * 是否默认配置
     */
    @Column(length = 1)
    @Schema(description = "是否作为默认配置")
    private String isDefault;

    /**
     * 状态
     */
    @Column(length = 1)
    @Schema(description = "服务提供商状态")
    private String state;
}
