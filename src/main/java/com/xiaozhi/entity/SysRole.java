package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 角色配置实体类
 *
 * @author Joey
 * @since 1.0
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
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
     * STT 服务 ID
     */
    @Schema(description = "STT 服务 ID")
    private Integer sttId;

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
     * 语音活动检测 - 能量阈值
     */
    @Schema(description = "语音活动检测 - 能量阈值")
    private Float vadEnergyTh;

    /**
     * 语音活动检测 - 静音毫秒数
     */
    @Schema(description = "语音活动检测 - 静音毫秒数")
    private Integer vadSilenceMs;

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
     * 温度参数，控制输出的随机性
     */
    @Schema(description = "温度参数")
    private Double temperature = 0.7;

    /**
     * Top-P 参数，控制输出的多样性
     */
    @Schema(description = "Top-P 参数")
    private Double topP = 1.0;

    /**
     * 记忆类型
     */
    @Column(length = 20)
    @Schema(description = "记忆类型")
    private String memoryType;

    /**
     * 状态 (1 启用 0 禁用)
     */
    @Column(length = 1)
    @Schema(description = "状态 (1 启用 0 禁用)")
    private String state;

    /**
     * 是否默认角色 (1 是 0 否)
     */
    @Column(length = 1)
    @Schema(description = "是否默认角色 (1 是 0 否)")
    private String isDefault;

    /**
     * 用户 ID
     */
    @Column(nullable = false)
    @Schema(description = "创建人用户 ID")
    private Integer userId;
}
