package com.xiaozhi.role;

import com.xiaozhi.common.model.resp.RoleDefaultsResp;
import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.req.RoleCreateReq;
import com.xiaozhi.common.model.req.RolePageReq;
import com.xiaozhi.common.model.req.RoleUpdateReq;
import com.xiaozhi.common.model.req.TestVoiceReq;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.common.model.resp.LocalSttResp;
import com.xiaozhi.common.model.resp.SherpaVoiceResp;
import com.xiaozhi.common.model.resp.TestVoiceResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.common.web.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 * 
 * @author Joey
 * 
 */

@Slf4j
@RestController
@RequestMapping("/api/role")
@Tag(name = "角色管理", description = "角色相关操作")
public class RoleController extends BaseController {

    @Resource
    private RoleAppService roleAppService;

    @Resource
    private RoleSpeechAppService roleSpeechAppService;

    /**
     * 角色查询
     *
     * @param req
     * @return roleList
     */
    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:role:api:list")
    @Operation(summary = "根据条件查询角色信息", description = "返回角色信息列表")
    public ApiResponse<PageResult<RoleResp>> list(@Valid RolePageReq req) {
        return ApiResponse.success(roleAppService.page(req, CurrentUser.id()));
    }

    @GetMapping("/defaults")
    @ResponseBody
    @SaCheckPermission("system:role:api:list")
    @Operation(summary = "角色参数默认值", description = "新建表单展示用；没改过的参数不必提交，后端按同一份默认补齐")
    public ApiResponse<RoleDefaultsResp> defaults() {
        return ApiResponse.success(RoleDefaultsResp.current());
    }

    /**
     * 角色信息更新
     *
     * @param roleId 角色ID
     * @param param 更新参数
     * @return
     */
    @PutMapping("/{roleId}")
    @ResponseBody
    @SaCheckPermission("system:role:api:update")
    @CheckOwner(resource = "role", id = "#roleId")
    @AuditLog(module = "角色管理", operation = "更新角色")
    @CheckOwner(resource = "config", id = "#param.modelId")
    @CheckOwner(resource = "config", id = "#param.sttId != null && #param.sttId > 0 ? #param.sttId : null")
    @CheckOwner(resource = "config", id = "#param.ttsId != null && #param.ttsId > 0 ? #param.ttsId : null")
    @Operation(summary = "更新角色信息", description = "更新语音助手角色配置")
    public ApiResponse<RoleResp> update(@PathVariable Integer roleId, @Valid @RequestBody RoleUpdateReq param) {
        return ApiResponse.success(roleAppService.update(roleId, param));
    }

    /**
     * 添加角色
     *
     * @param param 添加参数
     */
    @PostMapping("")
    @ResponseBody
    @SaCheckPermission("system:role:api:create")
    @AuditLog(module = "角色管理", operation = "创建角色")
    @CheckOwner(resource = "config", id = "#param.modelId")
    @CheckOwner(resource = "config", id = "#param.sttId != null && #param.sttId > 0 ? #param.sttId : null")
    @CheckOwner(resource = "config", id = "#param.ttsId != null && #param.ttsId > 0 ? #param.ttsId : null")
    @Operation(summary = "添加角色信息", description = "添加新的语音助手角色")
    public ApiResponse<RoleResp> create(@Valid @RequestBody RoleCreateReq param) {
        return ApiResponse.success(roleAppService.create(param, CurrentUser.id()));
    }

    /**
     * 删除角色
     *
     * @param roleId 角色ID
     * @return
     */
    @DeleteMapping("/{roleId}")
    @ResponseBody
    @SaCheckPermission("system:role:api:delete")
    @CheckOwner(resource = "role", id = "#roleId")
    @AuditLog(module = "角色管理", operation = "删除角色")
    @Operation(summary = "删除角色信息", description = "删除指定的语音助手角色")
    public ApiResponse<Void> delete(@PathVariable Integer roleId) {
        roleAppService.delete(roleId);
        return ApiResponse.success("删除成功");
    }

    /**
     * 扫描配置的本地 TTS 模型目录，动态返回所有可用的 sherpa-onnx 音色列表
     */
    @GetMapping("/sherpaVoices")
    @ResponseBody
    @SaCheckPermission("system:role:api:list")
    @Operation(summary = "获取本地 sherpa-onnx 音色列表", description = "扫描配置的本地 TTS 模型目录，自动识别模型类型和 speaker")
    public ApiResponse<List<SherpaVoiceResp>> listSherpaVoices() {
        return ApiResponse.success(roleSpeechAppService.sherpaVoices());
    }

    /**
     * 本地语音识别当前加载的模型。本地 provider 不进配置页，角色页的"本地识别"选项靠它显示到底是 SenseVoice 还是 Vosk
     */
    @GetMapping("/localStt")
    @ResponseBody
    @SaCheckPermission("system:role:api:list")
    @Operation(summary = "获取本地语音识别状态", description = "返回服务端启动时加载成功的本地 STT provider，模型都未就位时 provider 为空")
    public ApiResponse<LocalSttResp> localStt() {
        return ApiResponse.success(roleSpeechAppService.localStt());
    }

    @GetMapping("/testVoice")
    @ResponseBody
    @SaCheckPermission(value = {"system:role:api:create", "system:role:api:update"}, mode = SaMode.OR)
    @CheckOwner(resource = "config", id = "#param.provider != 'edge' ? #param.ttsId : null")
    @Operation(summary = "测试语音合成", description = "测试指定配置的语音合成效果")
    public ApiResponse<TestVoiceResp> testAudio(@Valid TestVoiceReq param) {
        return ApiResponse.success(roleSpeechAppService.preview(param));
    }
}
