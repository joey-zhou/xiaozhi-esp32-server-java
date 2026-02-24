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

import java.io.File;
import java.util.*;

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

    /**
     * 扫描本地 models/tts 目录，返回可用的 sherpa-onnx 本地模型音色列表
     */
    @GetMapping("/localVoices")
    @ResponseBody
    @Operation(summary = "获取本地模型音色", description = "扫描 models/tts 目录返回可用的 sherpa-onnx 本地 TTS 模型音色")
    public ResultMessage listLocalVoices() {
        try {
            List<Map<String, Object>> voices = new ArrayList<>();
            File ttsDir = new File("models/tts");
            if (!ttsDir.exists() || !ttsDir.isDirectory()) {
                ResultMessage result = ResultMessage.success();
                result.put("data", voices);
                return result;
            }

            File[] modelDirs = ttsDir.listFiles(File::isDirectory);
            if (modelDirs == null) {
                ResultMessage result = ResultMessage.success();
                result.put("data", voices);
                return result;
            }

            for (File modelDir : modelDirs) {
                String dirName = modelDir.getName();
                String modelType = detectLocalModelType(modelDir);

                switch (modelType) {
                    case "kokoro" -> {
                        // Kokoro 模型支持多 speaker
                        String[] defaultSpeakers = {"0", "1", "2", "3", "4", "5", "6", "7"};
                        for (String sid : defaultSpeakers) {
                            Map<String, Object> voice = new LinkedHashMap<>();
                            voice.put("label", dirName + " Speaker-" + sid);
                            voice.put("value", "kokoro:" + sid);
                            voice.put("gender", "");
                            voice.put("provider", "sherpa-onnx");
                            voice.put("model", dirName);
                            voice.put("modelPath", modelDir.getPath());
                            voices.add(voice);
                        }
                    }
                    case "matcha" -> {
                        Map<String, Object> voice = new LinkedHashMap<>();
                        voice.put("label", dirName + " (Matcha)");
                        voice.put("value", "matcha:0");
                        voice.put("gender", "");
                        voice.put("provider", "sherpa-onnx");
                        voice.put("model", dirName);
                        voice.put("modelPath", modelDir.getPath());
                        voices.add(voice);
                    }
                    default -> {
                        // VITS 模型
                        if (dirName.contains("aishell3") || dirName.contains("vctk")) {
                            // 多 speaker 模型
                            String[] defaultSpeakers = {"0", "10", "21", "33", "50", "66", "88", "99"};
                            for (String sid : defaultSpeakers) {
                                Map<String, Object> voice = new LinkedHashMap<>();
                                voice.put("label", dirName + " Speaker-" + sid);
                                voice.put("value", "vits:" + sid);
                                voice.put("gender", "");
                                voice.put("provider", "sherpa-onnx");
                                voice.put("model", dirName);
                                voice.put("modelPath", modelDir.getPath());
                                voices.add(voice);
                            }
                        } else {
                            // 单 speaker 模型
                            Map<String, Object> voice = new LinkedHashMap<>();
                            voice.put("label", dirName + " (VITS)");
                            voice.put("value", "vits:0");
                            voice.put("gender", "");
                            voice.put("provider", "sherpa-onnx");
                            voice.put("model", dirName);
                            voice.put("modelPath", modelDir.getPath());
                            voices.add(voice);
                        }
                    }
                }
            }

            ResultMessage result = ResultMessage.success();
            result.put("data", voices);
            return result;
        } catch (Exception e) {
            logger.error("扫描本地TTS模型失败: " + e.getMessage(), e);
            return ResultMessage.error("扫描本地模型失败");
        }
    }

    /**
     * 检测本地模型目录的模型类型
     */
    private String detectLocalModelType(File dir) {
        if (new File(dir, "voices.bin").exists()) return "kokoro";
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().contains("vocoder")) return "matcha";
            }
        }
        return "vits";
    }
}