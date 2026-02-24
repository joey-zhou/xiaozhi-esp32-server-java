package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.dto.param.RoleAddParam;
import com.xiaozhi.dto.param.RoleUpdateParam;
import com.xiaozhi.dto.param.TestVoiceParam;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.BeanUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询角色信息", description = "返回角色信息列表")
    public ResultMessage list(SysRole role, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            role.setUserId(CmsUtils.getUserId());
            List<SysRole> roleList = roleService.query(role, pageFilter);

            ResultMessage result = ResultMessage.success();
            result.put("data", DtoConverter.toPageInfo(roleList, DtoConverter::toRoleDTOList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
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
    @Operation(summary = "更新角色信息", description = "更新语音助手角色配置")
    public ResultMessage update(@PathVariable Integer roleId, @Valid @RequestBody RoleUpdateParam param) {
        try {
            SysRole role = new SysRole();
            BeanUtils.copyProperties(param, role);
            role.setRoleId(roleId);
            role.setUserId(CmsUtils.getUserId());

            roleService.update(role);

            // 返回更新后的角色信息
            SysRole updatedRole = roleService.selectRoleById(roleId);
            return ResultMessage.success(DtoConverter.toRoleDTO(updatedRole));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加角色
     *
     * @param param 添加参数
     */
    @PostMapping("")
    @ResponseBody
    @Operation(summary = "添加角色信息", description = "添加新的语音助手角色")
    public ResultMessage create(@Valid @RequestBody RoleAddParam param) {
        try {
            SysRole role = new SysRole();
            BeanUtils.copyProperties(param, role);
            role.setUserId(CmsUtils.getUserId());

            roleService.add(role);

            // 返回新增的角色信息
            return ResultMessage.success(DtoConverter.toRoleDTO(role));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除角色
     *
     * @param roleId 角色ID
     * @return
     */
    @DeleteMapping("/{roleId}")
    @ResponseBody
    @Operation(summary = "删除角色信息", description = "删除指定的语音助手角色")
    public ResultMessage delete(@PathVariable Integer roleId) {
        try {
            // 验证角色是否属于当前用户
            SysRole role = roleService.selectRoleById(roleId);
            if (role == null) {
                return ResultMessage.error("角色不存在");
            }
            logger.error("用户ID：" + CmsUtils.getUserId() + "，角色用户ID：" + role.getUserId());
            if (!role.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权删除该角色");
            }

            roleService.deleteById(roleId);
            return ResultMessage.success("删除成功");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("删除失败");
        }
    }

    @GetMapping("/testVoice")
    @ResponseBody
    @Operation(summary = "测试语音合成", description = "测试指定配置的语音合成效果")
    public ResultMessage testAudio(@Valid TestVoiceParam param) {
        SysConfig config = null;
        try {
            if (!param.getProvider().equals("edge")) {
                config = configService.selectConfigById(param.getTtsId());
            }
            String audioFilePath = ttsService.getTtsService(config, param.getVoiceName(), param.getTtsPitch(), param.getTtsSpeed())
                    .textToSpeech(param.getMessage());

            ResultMessage result = ResultMessage.success();
            result.put("data", audioFilePath);
            return result;
        } catch (IndexOutOfBoundsException e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("请先到语音合成配置页面配置对应Key");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

}