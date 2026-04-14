package com.xiaozhi.template.domain.repository;

import com.xiaozhi.template.domain.Template;

import java.util.Optional;

/**
 * Template 聚合根仓储接口。
 * <p>
 * save() 时自动维护"同 userId 下唯一默认模板"不变式。
 */
public interface TemplateRepository {

    Optional<Template> findById(Integer templateId);

    /**
     * 持久化聚合根（新建或更新）。
     * <p>若检测到 DEFAULT_CHANGED 信号，先 resetDefault 再保存。
     */
    void save(Template template);

    /** 软删除（state=disabled）。 */
    void delete(Integer templateId);
}
