package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 提示词模板实体类
 *
 * @author Joey
 *
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "提示词模板信息")
@Entity
@Table(name = "sys_template")
public class SysTemplate extends Base<SysTemplate> {

    /**
     * 模板ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "模板ID")
    private Integer templateId;
    /**
     * 用户 ID
     */
    @Column(nullable = false)
    @Schema(description = "用户 ID")
    private Integer userId;

    /**
     * 模板名称
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "模板名称")
    private String templateName;

    /**
     * 模板描述
     */
    @Column(length = 500)
    @Schema(description = "模板描述")
    private String templateDesc;

    /**
     * 模板内容
     */
    @Column(nullable = false, columnDefinition = "text")
    @Schema(description = "模板内容")
    private String templateContent;

    /**
     * 模板分类
     */
    @Column(length = 50)
    @Schema(description = "模板分类")
    private String category;

    /**
     * 是否默认模板(1是 0否)
     */
    @Column(length = 1)
    @Schema(description = "是否默认模板(1是 0否)")
    private String isDefault;

    /**
     * 状态(1启用 0禁用)
     */
    @Column(length = 1)
    @Schema(description = "状态(1启用 0禁用)")
    private String state;
}