package com.xiaozhi.role;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.req.RoleCreateReq;
import com.xiaozhi.common.model.req.RolePageReq;
import com.xiaozhi.common.model.req.RoleUpdateReq;
import com.xiaozhi.common.model.req.TestVoiceReq;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * 角色管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/role")
@Tag(name = "角色管理", description = "角色相关操作")
public class RoleController extends BaseController {

    @Resource
    private RoleAppService roleAppService;

    @Resource
    private SherpaVoiceService sherpaVoiceService;

    @Resource
    private TtsServiceFactory ttsService;

    @Resource
    private ConfigService configService;

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
    public ApiResponse<?> list(@Valid RolePageReq req) {
        return ApiResponse.success(roleAppService.page(req, StpUtil.getLoginIdAsInt()));
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
    @CheckOwner(resource = "config", id = "#param.sttId")
    @CheckOwner(resource = "config", id = "#param.ttsId")
    @Operation(summary = "更新角色信息", description = "更新语音助手角色配置")
    public ApiResponse<?> update(@PathVariable Integer roleId, @Valid @RequestBody RoleUpdateReq param) {
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
    @CheckOwner(resource = "config", id = "#param.sttId")
    @CheckOwner(resource = "config", id = "#param.ttsId")
    @Operation(summary = "添加角色信息", description = "添加新的语音助手角色")
    public ApiResponse<?> create(@Valid @RequestBody RoleCreateReq param) {
        return ApiResponse.success(roleAppService.create(param, StpUtil.getLoginIdAsInt()));
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
    public ApiResponse<?> delete(@PathVariable Integer roleId) {
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
    public ApiResponse<?> listSherpaVoices() {
        return ApiResponse.success(sherpaVoiceService.listVoices());
    }

    @GetMapping("/testVoice")
    @ResponseBody
    @SaCheckPermission("system:role:api:list")
    @CheckOwner(resource = "config", id = "#param.provider != 'edge' ? #param.ttsId : null")
    @Operation(summary = "测试语音合成", description = "测试指定配置的语音合成效果")
    public ApiResponse<?> testAudio(@Valid TestVoiceReq param) {
        ConfigBO config = null;
        if (!param.getProvider().equals("edge")) {
            if (param.getTtsId() == null) {
                throw new IllegalArgumentException("非 edge 提供方必须指定语音配置");
            }
            config = configService.getBO(param.getTtsId());
            if (config == null) {
                throw new ResourceNotFoundException("语音配置不存在或无权访问");
            }
        }

        try {
            Path audioFilePath = ttsService.getTtsService(config, param.getVoiceName(), param.getTtsPitch(), param.getTtsSpeed())
                    .textToSpeech(param.getMessage());

            return ApiResponse.success("操作成功", audioFilePath != null ? audioFilePath.toString() : null);
        } catch (IndexOutOfBoundsException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException("请先到语音合成配置页面配置对应Key", e);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new OperationFailedException("测试语音合成失败", e);
        }
    }
}
