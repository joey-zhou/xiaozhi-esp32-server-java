package com.xiaozhi.template.domain;

import com.xiaozhi.common.model.bo.TemplateBO;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Template 聚合根 —— 表示一条对话提示词模板。
 * <p>
 * 不变式：同一 userId 下最多一条默认模板（由 TemplateRepository.save 维护）。
 */
@Getter
public class Template {

    public static final String STATE_ENABLED  = "1";
    public static final String STATE_DISABLED = "0";

    public enum DomainSignal { DEFAULT_CHANGED, UPDATED, DISABLED }

    private Integer       templateId;
    private Integer       userId;
    private String        templateName;
    private String        templateDesc;
    private String        templateContent;
    private String        category;
    private String        state;
    private boolean       isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private final List<DomainSignal> signals = new ArrayList<>();

    public Template() {}

    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    public static Template newTemplate(Integer userId, String templateName, String templateDesc,
                                       String templateContent, String category, boolean isDefault) {
        Template t = new Template();
        t.userId          = userId;
        t.templateName    = templateName;
        t.templateDesc    = templateDesc;
        t.templateContent = templateContent;
        t.category        = category;
        t.state           = STATE_ENABLED;
        t.isDefault       = isDefault;
        if (isDefault) t.signals.add(DomainSignal.DEFAULT_CHANGED);
        return t;
    }

    public static Template newTemplate(Integer userId, TemplateBO bo) {
        return newTemplate(userId, bo.getTemplateName(), bo.getTemplateDesc(),
                bo.getTemplateContent(), bo.getCategory(),
                "1".equals(bo.getIsDefault()));
    }

    /** 从持久层重建聚合根（Repository 专用，不产生任何信号）。 */
    public static Template reconstitute(Integer templateId, Integer userId,
                                        String templateName, String templateDesc,
                                        String templateContent, String category,
                                        String state, boolean isDefault,
                                        LocalDateTime createTime, LocalDateTime updateTime) {
        Template t = new Template();
        t.templateId      = templateId;
        t.userId          = userId;
        t.templateName    = templateName;
        t.templateDesc    = templateDesc;
        t.templateContent = templateContent;
        t.category        = category;
        t.state           = state;
        t.isDefault       = isDefault;
        t.createTime      = createTime;
        t.updateTime      = updateTime;
        return t;
    }

    // ── 行为方法 ──────────────────────────────────────────────────────────────

    public void setAsDefault() {
        if (!this.isDefault) {
            this.isDefault = true;
            signals.add(DomainSignal.DEFAULT_CHANGED);
        }
    }

    public void clearDefault() {
        this.isDefault = false;
    }

    public void update(TemplateBO bo) {
        update(bo.getTemplateName(), bo.getTemplateDesc(), bo.getTemplateContent(),
                bo.getCategory(), bo.getIsDefault() == null ? null : "1".equals(bo.getIsDefault()));
    }

    public void update(String templateName, String templateDesc,
                       String templateContent, String category, Boolean isDefault) {
        if (templateName    != null) this.templateName    = templateName;
        if (templateDesc    != null) this.templateDesc    = templateDesc;
        if (templateContent != null) this.templateContent = templateContent;
        if (category        != null) this.category        = category;
        if (isDefault != null) {
            if (isDefault && !this.isDefault) {
                this.isDefault = true;
                signals.add(DomainSignal.DEFAULT_CHANGED);
            } else if (!isDefault) {
                this.isDefault = false;
            }
        }
        signals.add(DomainSignal.UPDATED);
    }

    public void disable() {
        this.state     = STATE_DISABLED;
        this.isDefault = false;
        signals.add(DomainSignal.DISABLED);
    }

    /** insert 后由 Repository 回填自增主键，不产生信号。 */
    public void assignId(Integer templateId) {
        this.templateId = templateId;
    }

    public List<DomainSignal> pullSignals() {
        List<DomainSignal> s = List.copyOf(signals);
        signals.clear();
        return s;
    }
}
