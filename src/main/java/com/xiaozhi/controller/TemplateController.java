package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dto.param.TemplateAddParam;
import com.xiaozhi.dto.param.TemplateUpdateParam;
import com.xiaozhi.entity.SysTemplate;
import com.xiaozhi.service.SysTemplateService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板控制器
 */
@RestController
@RequestMapping("/api/template")
@Tag(name = "提示词模板管理", description = "提示词模板相关操作")
public class TemplateController {

    @Resource
    private SysTemplateService templateService;

    /**
     * 查询模板列表
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询角色模板", description = "返回模板列表")
    public ResultMessage list(SysTemplate template) {
        try {
            template.setUserId(CmsUtils.getUserId());
            List<SysTemplate> templateList = templateService.query(template);

            ResultMessage result = ResultMessage.success();
            result.put("data", DtoConverter.toPageInfo(templateList, DtoConverter::toTemplateDTOList));
            return result;
        } catch (Exception e) {
            return ResultMessage.error(e.getMessage());
        }
    }

    /**
     * 添加模板
     */
    @PostMapping("")
    @ResponseBody
    @Operation(summary = "添加角色模板", description = "添加新的提示词模板")
    public ResultMessage create(@Valid @RequestBody TemplateAddParam param) {
            try {
            SysTemplate template = new SysTemplate();
            template.setTemplateName(param.getTemplateName());
            template.setTemplateContent(param.getTemplateContent());
            template.setTemplateDesc(param.getTemplateDesc());
            template.setUserId(CmsUtils.getUserId());

            int rows = templateService.add(template);
            if (rows > 0) {
                return ResultMessage.success(DtoConverter.toTemplateDTO(template));
            }
            return ResultMessage.error("添加模板失败");
            } catch (Exception e) {
                return ResultMessage.error(e.getMessage());
            }
    }

    /**
     * 修改模板
     */
    @PutMapping("/{templateId}")
    @ResponseBody
    @Operation(summary = "更新角色模板", description = "更新提示词模板信息")
    public ResultMessage update(@PathVariable Integer templateId, @Valid @RequestBody TemplateUpdateParam param) {
        try {
            SysTemplate template = new SysTemplate();
            template.setTemplateId(templateId);
            template.setTemplateName(param.getTemplateName());
            template.setTemplateContent(param.getTemplateContent());
            template.setTemplateDesc(param.getTemplateDesc());
            template.setUserId(CmsUtils.getUserId());

            int rows = templateService.update(template);
            if (rows > 0) {
                // 返回更新后的模板
                SysTemplate updatedTemplate = templateService.selectTemplateById(templateId);
                return ResultMessage.success(DtoConverter.toTemplateDTO(updatedTemplate));
            }
            return ResultMessage.error("修改模板失败");
        } catch (Exception e) {
            return ResultMessage.error(e.getMessage());
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{templateId}")
    @ResponseBody
    @Operation(summary = "删除角色模板", description = "删除提示词模板（逻辑删除）")
    public ResultMessage delete(@PathVariable Integer templateId) {
        try {
            int rows = templateService.delete(templateId);
            if (rows > 0) {
                return ResultMessage.success("删除成功");
            }
            return ResultMessage.error("删除模板失败");
        } catch (Exception e) {
            return ResultMessage.error(e.getMessage());
        }
    }

}