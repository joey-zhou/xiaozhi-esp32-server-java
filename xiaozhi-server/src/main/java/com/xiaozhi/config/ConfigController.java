package com.xiaozhi.config;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.config.ConfigAppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


/**
 * 配置管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/config")
@Tag(name = "配置管理", description = "配置相关操作")
public class ConfigController extends BaseController {

    @Resource
    private ConfigAppService configAppService;

    /**
     * 配置查询
     *
     * @param config
     * @return configList
     */
    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:config:api:list")
    @Operation(summary = "根据条件查询配置", description = "返回配置信息列表")
    public ApiResponse<?> list(@Valid ConfigPageReq req) {
        return ApiResponse.success(configAppService.page(req, StpUtil.getLoginIdAsInt()));
    }

    /**
     * 配置信息更新
     *
     * @param configId 配置ID
     * @param param 更新参数
     * @return
     */
    @PutMapping("/{configId}")
    @ResponseBody
    @SaCheckPermission("system:config:api:update")
    @CheckOwner(resource = "config", id = "#configId")
    @AuditLog(module = "配置管理", operation = "更新配置")
    @Operation(summary = "更新配置信息", description = "更新LLM/STT/TTS配置")
    public ApiResponse<?> update(@PathVariable Integer configId, @Valid @RequestBody ConfigUpdateReq req) {
        return ApiResponse.success(configAppService.update(configId, req));
    }

    /**
     * 添加配置
     *
     * @param param 添加参数
     */
    @PostMapping("")
    @ResponseBody
    @SaCheckPermission("system:config:api:create")
    @AuditLog(module = "配置管理", operation = "创建配置")
    @Operation(summary = "添加配置信息", description = "添加新的LLM/STT/TTS配置")
    public ApiResponse<?> create(@Valid @RequestBody ConfigCreateReq req) {
        return ApiResponse.success(configAppService.create(req, StpUtil.getLoginIdAsInt()));
    }

    /**
     * 删除配置信息
     *
     * @param configId 配置ID
     * @return
     */
    @DeleteMapping("/{configId}")
    @ResponseBody
    @SaCheckPermission("system:config:api:delete")
    @CheckOwner(resource = "config", id = "#configId")
    @AuditLog(module = "配置管理", operation = "删除配置")
    @Operation(summary = "删除配置信息", description = "软删除指定配置")
    public ApiResponse<?> delete(@PathVariable Integer configId) {
        configAppService.delete(configId);
        return ApiResponse.success("删除成功");
    }
}
