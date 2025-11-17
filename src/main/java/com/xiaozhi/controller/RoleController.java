package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private SysRoleService roleService;

    @Resource
    private TtsServiceFactory ttsService;

    @Resource
    private SysConfigService configService;

    /**
     * 角色查询
     * 
     * @param role
     * @return roleList
     */
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询角色信息", description = "返回角色信息列表")
    public ResultMessage query(SysRole role, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            role.setUserId(CmsUtils.getUserId());
            List<SysRole> roleList = roleService.query(role, pageFilter);
            ResultMessage result = ResultMessage.success();
            result.put("data", new PageInfo<>(roleList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 角色信息更新
     * 
     * @param role
     * @return
     */
    @PostMapping("/update")
    @ResponseBody
    @Operation(summary = "更新角色信息", description = "返回更新结果")
    public ResultMessage update(SysRole role) {
        try {
            role.setUserId(CmsUtils.getUserId());
            roleService.update(role);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加角色
     * 
     * @param role
     */
    @PostMapping("/add")
    @ResponseBody
    @Operation(summary = "添加角色信息", description = "返回添加结果")
    public ResultMessage add(SysRole role) {
        try {
            role.setUserId(CmsUtils.getUserId());
            roleService.add(role);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    @GetMapping("/testVoice")
    @ResponseBody
    @Operation(summary = "测试语音合成", description = "返回语音合成结果")
    public ResultMessage testAudio(
        @Parameter(description = "消息文本") String message,
        @Parameter(description = "语音合成提供方") String provider,
        @Parameter(description = "TTS ID") Integer ttsId,
        @Parameter(description = "音色名称") String voiceName,
        @Parameter(description = "语音音调(0.5-2.0)") Float ttsPitch,
        @Parameter(description = "语音语速(0.5-2.0)") Float ttsSpeed) {
        SysConfig config = null;
        try {
            if (!provider.equals("edge")) {
                config = configService.selectConfigById(ttsId);
            }
            String audioFilePath = ttsService.getTtsService(config, voiceName, ttsPitch, ttsSpeed).textToSpeech(message);

            ResultMessage result = ResultMessage.success();
            result.put("data", audioFilePath);
            return result;
        } catch (IndexOutOfBoundsException e) {
            return ResultMessage.error("请先到语音合成配置页面配置对应Key");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }
}