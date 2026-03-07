package com.xiaozhi.repository;

import com.xiaozhi.entity.SysTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提示词模板数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysTemplateRepository extends JpaRepository<SysTemplate, Integer>, JpaSpecificationExecutor<SysTemplate> {

    /**
     * 根据模板 ID 查询模板
     */
    @Query(value = "SELECT * FROM sys_template WHERE templateId = :templateId", nativeQuery = true)
    SysTemplate findByTemplateId(@Param("templateId") Integer templateId);

    /**
     * 根据用户 ID 查询模板列表
     */
    @Query(value = "SELECT * FROM sys_template WHERE userId = :userId ORDER BY createTime DESC", nativeQuery = true)
    List<SysTemplate> findByUserId(@Param("userId") Integer userId);

    /**
     * 根据模板名称查询模板
     */
    @Query(value = "SELECT * FROM sys_template WHERE templateName = :templateName", nativeQuery = true)
    SysTemplate findByTemplateName(@Param("templateName") String templateName);

    /**
     * 重置默认模板
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_template SET isDefault = '0' WHERE userId = :userId", nativeQuery = true)
    int resetDefault(@Param("userId") Integer userId);

    /**
     * 根据模板 ID 删除模板
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_template WHERE templateId = :templateId", nativeQuery = true)
    int deleteTemplateById(@Param("templateId") Integer templateId);

    /**
     * 查询模板列表
     */
    @Query(value = "SELECT * FROM sys_template WHERE 1=1 " +
            "AND (:userId IS NULL OR userId = :userId) " +
            "AND (:templateName IS NULL OR :templateName = '' OR templateName LIKE %:templateName%) " +
            "AND (:category IS NULL OR :category = '' OR category = :category) " +
            "ORDER BY createTime DESC", nativeQuery = true)
    List<SysTemplate> findTemplates(
            @Param("userId") Integer userId,
            @Param("templateName") String templateName,
            @Param("category") String category);

    default SysTemplate findTemplateById(Integer templateId) {
        return findByTemplateId(templateId);
    }

    default int add(SysTemplate template) {
        save(template);
        return 1;
    }

    default int update(SysTemplate template) {
        save(template);
        return 1;
    }

    default int delete(Integer templateId) {
        return deleteTemplateById(templateId);
    }

    default List<SysTemplate> query(SysTemplate template) {
        return findTemplates(template.getUserId(), template.getTemplateName(), template.getCategory());
    }

    default SysTemplate selectTemplateById(Integer templateId) {
        return findTemplateById(templateId);
    }

    default int resetDefault(SysTemplate template) {
        return resetDefault(template.getUserId());
    }
}
