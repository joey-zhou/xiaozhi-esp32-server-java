package com.xiaozhi.template;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.req.TemplateCreateReq;
import com.xiaozhi.common.model.req.TemplatePageReq;
import com.xiaozhi.common.model.req.TemplateUpdateReq;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.template.TemplateAppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 提示词模板控制器
 */
@RestController
@RequestMapping("/api/template")
@Tag(name = "提示词模板管理", description = "提示词模板相关操作")
public class TemplateController extends BaseController {

    @Resource
    private TemplateAppService templateAppService;

    /**
     * 查询模板列表
     */
    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:prompt-template:api:list")
    @Operation(summary = "根据条件查询角色模板", description = "返回模板列表")
    public ApiResponse<?> list(@Valid TemplatePageReq req) {
        return ApiResponse.success(templateAppService.page(req, StpUtil.getLoginIdAsInt()));
    }

    /**
     * 添加模板
     */
    @PostMapping("")
    @ResponseBody
    @SaCheckPermission("system:prompt-template:api:create")
    @AuditLog(module = "模板管理", operation = "创建模板")
    @Operation(summary = "添加角色模板", description = "添加新的提示词模板")
    public ApiResponse<?> create(@Valid @RequestBody TemplateCreateReq req) {
        return ApiResponse.success(templateAppService.create(req, StpUtil.getLoginIdAsInt()));
    }

    /**
     * 修改模板
     */
    @PutMapping("/{templateId}")
    @ResponseBody
    @SaCheckPermission("system:prompt-template:api:update")
    @CheckOwner(resource = "template", id = "#templateId")
    @AuditLog(module = "模板管理", operation = "更新模板")
    @Operation(summary = "更新角色模板", description = "更新提示词模板信息")
    public ApiResponse<?> update(@PathVariable Integer templateId, @Valid @RequestBody TemplateUpdateReq req) {
        return ApiResponse.success(templateAppService.update(templateId, req));
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{templateId}")
    @ResponseBody
    @SaCheckPermission("system:prompt-template:api:delete")
    @CheckOwner(resource = "template", id = "#templateId")
    @AuditLog(module = "模板管理", operation = "删除模板")
    @Operation(summary = "删除角色模板", description = "删除提示词模板（逻辑删除）")
    public ApiResponse<?> delete(@PathVariable Integer templateId) {
        templateAppService.delete(templateId);
        return ApiResponse.success("删除成功");
    }
}
