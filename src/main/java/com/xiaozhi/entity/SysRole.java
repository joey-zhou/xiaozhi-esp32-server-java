package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 角色配置实体类
 *
 * @author Joey
 * @since 1.0
 */
@Getter
@Setter
@Accessors(chain = true)
@JsonIgnoreProperties({ "code" })
@Schema(description = "角色信息")
@Entity
@Table(name = "sys_role")
public class SysRole extends Base<SysRole> {
    /**
     * 角色 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "角色 ID")
    private Integer roleId;

    /**
     * 角色名称
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色描述
     */
    @Column(columnDefinition = "text")
    @Schema(description = "角色描述")
    private String roleDesc;

    /**
     * 角色头像
     */
    @Column(length = 255)
    @Schema(description = "角色头像")
    private String avatar;

    /**
     * 语音名称
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "语音名称")
    private String voiceName;

    /**
     * 语音音调 (0.5-2.0, 默认 1.0)
     */
    @Schema(description = "语音音调")
    private Float ttsPitch = 1.0f;

    /**
     * 语音语速 (0.5-2.0, 默认 1.0)
     */
    @Schema(description = "语音语速")
    private Float ttsSpeed = 1.0f;

    /**
     * 状态 (1 启用 0 禁用)
     */
    @Column(length = 1)
    @Schema(description = "状态 (1 启用 0 禁用)")
    private String state;

    /**
     * TTS 服务 ID
     */
    @Schema(description = "TTS 服务 ID")
    private Integer ttsId;

    /**
     * 模型 ID
     */
    @Schema(description = "模型 ID")
    private Integer modelId;

    /**
     * 模型名称
     */
    @Transient
    @Schema(description = "模型名称")
    private String modelName;

    /**
     * STT 服务 ID
     */
    @Schema(description = "STT 服务 ID")
    private Integer sttId;

    /**
     * 温度参数，控制输出的随机性
     */
    @Schema(description = "温度参数")
    private Double temperature = 0.7;

    /**
     * Top-P 参数，控制输出的多样性
     */
    @Schema(description = "Top-P 参数")
    private Double topP = 0.9;

    /**
     * 语音活动检测 - 能量阈值
     */
    @Schema(description = "语音活动检测 - 能量阈值")
    private Float vadEnergyTh;

    /**
     * 语音活动检测 - 语音阈值
     */
    @Schema(description = "语音活动检测 - 语音阈值")
    private Float vadSpeechTh;

    /**
     * 语音活动检测 - 静音阈值
     */
    @Schema(description = "语音活动检测 - 静音阈值")
    private Float vadSilenceTh;

    /**
     * 语音活动检测 - 静音毫秒数
     */
    @Schema(description = "语音活动检测 - 静音毫秒数")
    private Integer vadSilenceMs;

    /**
     * 模型提供商
     */
    @Transient
    @Schema(description = "模型提供商")
    private String modelProvider;

    /**
     * TTS 服务提供商
     */
    @Transient
    @Schema(description = "TTS 服务提供商")
    private String ttsProvider;

    /**
     * 是否默认角色 (1 是 0 否)
     */
    @Column(length = 1)
    @Schema(description = "是否默认角色 (1 是 0 否)")
    private String isDefault;

    /**
     * 总设备数
     */
    @Transient
    @Schema(description = "总设备数")
    private Integer totalDevice;

    /**
     * 知识库 ID
     */
    @Transient
    @Schema(description = "知识库 ID")
    private String datasetId;

    /**
     * 记忆类型
     */
    @Column(length = 20)
    @Schema(description = "记忆类型")
    private String memoryType;
}
