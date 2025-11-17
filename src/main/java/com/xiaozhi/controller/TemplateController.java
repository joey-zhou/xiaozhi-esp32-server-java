package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysTemplate;
import com.xiaozhi.service.SysTemplateService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询角色模板", description = "返回模板列表")
    public ResultMessage query(SysTemplate template) {
        try {
            template.setUserId(CmsUtils.getUserId());
            List<SysTemplate> templateList = templateService.query(template);
            ResultMessage result = ResultMessage.success();
            result.put("data", new PageInfo<>(templateList));
            return result;
        } catch (Exception e) {
            return ResultMessage.error(e.getMessage());
        }
    }

    /**
     * 添加模板
     */
    @PostMapping("/add")
    @ResponseBody
    @Operation(summary = "添加角色模板", description = "返回添加结果")
    public ResultMessage add(SysTemplate template) {
            try {
            template.setUserId(CmsUtils.getUserId());
            int rows = templateService.add(template);
            return rows > 0 ? ResultMessage.success() : ResultMessage.error("添加模板失败");
            } catch (Exception e) {
                return ResultMessage.error(e.getMessage());
            }
    }

    /**
     * 修改模板
     */
    @PostMapping("/update")
    @ResponseBody
    @Operation(summary = "更新角色模板", description = "返回更新结果")
    public ResultMessage update(SysTemplate template) {
        try {
            template.setUserId(CmsUtils.getUserId());
            int rows = templateService.update(template);
            return rows > 0 ? ResultMessage.success() : ResultMessage.error("修改模板失败");
        } catch (Exception e) {
            return ResultMessage.error(e.getMessage());
        }
    }

}