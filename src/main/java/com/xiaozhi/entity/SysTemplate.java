package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 提示词模板实体类
 *
 * @author Joey
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "提示词模板信息")
@Entity
@Table(name = "sys_template")
@DynamicUpdate
@DynamicInsert
public class SysTemplate extends Base<SysTemplate> {

    /**
     * 模板 ID
     */
    @Id
    @Column(name = "template_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "模板 ID")
    private Integer templateId;

    /**
     * 模板名称
     */
    @Column(name = "template_name", nullable = false, length = 100)
    @Schema(description = "模板名称")
    private String templateName;

    /**
     * 模板描述
     */
    @Column(name = "template_desc", length = 500)
    @Schema(description = "模板描述")
    private String templateDesc;

    /**
     * 模板内容
     */
    @Column(name = "template_content", nullable = false, columnDefinition = "text")
    @Schema(description = "模板内容")
    private String templateContent;

    /**
     * 模板分类
     */
    @Column(length = 50)
    @Schema(description = "模板分类")
    private String category;

    /**
     * 是否默认模板 (1 是 0 否)
     */
    @Column(name = "is_default", length = 1)
    @Schema(description = "是否默认模板 (1 是 0 否)")
    private String isDefault;

    /**
     * 状态 (1 启用 0 禁用)
     */
    @Column(length = 1)
    @Schema(description = "状态 (1 启用 0 禁用)")
    private String state;
}
