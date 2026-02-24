package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.dto.param.ConfigAddParam;
import com.xiaozhi.dto.param.ConfigUpdateParam;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.BeanUtils;

import java.util.Objects;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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
    private SysConfigService configService;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    @Resource
    private SttServiceFactory sttServiceFactory;

    /**
     * 配置查询
     *
     * @param config
     * @return configList
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询配置", description = "返回配置信息列表")
    public ResultMessage list(SysConfig config, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysConfig> configList = configService.query(config, pageFilter);

            ResultMessage result = ResultMessage.success();
            result.put("data", DtoConverter.toPageInfo(configList, DtoConverter::toConfigDTOList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
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
    @Operation(summary = "更新配置信息", description = "更新LLM/STT/TTS配置")
    public ResultMessage update(@PathVariable Integer configId, @Valid @RequestBody ConfigUpdateParam param) {
        try {
            SysConfig config = new SysConfig();
            BeanUtils.copyProperties(param, config);
            config.setConfigId(configId);
            config.setUserId(CmsUtils.getUserId());

            SysConfig oldSysConfig = configService.selectConfigById(config.getConfigId());
            int rows = configService.update(config);
            if (rows > 0) {
                if (oldSysConfig != null) {
                    if ("stt".equals(oldSysConfig.getConfigType())
                            && !Objects.equals(oldSysConfig.getApiKey(), config.getApiKey())) {
                        sttServiceFactory.removeCache(oldSysConfig);
                    } else if ("tts".equals(oldSysConfig.getConfigType())
                            && !Objects.equals(oldSysConfig.getApiKey(), config.getApiKey())) {
                        ttsServiceFactory.removeCache(oldSysConfig);
                    }
                }

                // 返回更新后的配置信息
                SysConfig updatedConfig = configService.selectConfigById(configId);
                return ResultMessage.success(DtoConverter.toConfigDTO(updatedConfig));
            }
            return ResultMessage.error("更新失败");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加配置
     *
     * @param param 添加参数
     */
    @PostMapping("")
    @ResponseBody
    @Operation(summary = "添加配置信息", description = "添加新的LLM/STT/TTS配置")
    public ResultMessage create(@Valid @RequestBody ConfigAddParam param) {
        try {
            SysConfig config = new SysConfig();
            BeanUtils.copyProperties(param, config);
            config.setUserId(CmsUtils.getUserId());

            configService.add(config);

            // 返回新增的配置信息(不包含敏感字段)
            return ResultMessage.success(DtoConverter.toConfigDTO(config));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

}