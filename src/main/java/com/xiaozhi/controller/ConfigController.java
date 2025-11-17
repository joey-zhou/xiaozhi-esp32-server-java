package com.xiaozhi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

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
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询配置", description = "返回配置信息列表")
    public ResultMessage query(SysConfig config, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysConfig> configList = configService.query(config, pageFilter);
            ResultMessage result = ResultMessage.success();
            result.put("data", new PageInfo<>(configList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 配置信息更新
     * 
     * @param config
     * @return
     */
    @PostMapping("/update")
    @ResponseBody
    @Operation(summary = "更新配置信息", description = "返回更新结果")
    public ResultMessage update(SysConfig config) {
        try {
            config.setUserId(CmsUtils.getUserId());
            SysConfig oldSysConfig = configService.selectConfigById(config.getConfigId());
            int rows = configService.update(config);
            if (rows > 0) {
                if (oldSysConfig != null) {
                    if ("stt".equals(oldSysConfig.getConfigType())
                            && !oldSysConfig.getApiKey().equals(config.getApiKey())) {
                        sttServiceFactory.removeCache(oldSysConfig);
                    } else if ("tts".equals(oldSysConfig.getConfigType())
                            && !oldSysConfig.getApiKey().equals(config.getApiKey())) {
                        ttsServiceFactory.removeCache(oldSysConfig);
                    }
                }
            }
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加配置
     * 
     * @param config
     */
    @PostMapping("/add")
    @ResponseBody
    @Operation(summary = "添加配置信息", description = "返回添加结果")
    public ResultMessage add(SysConfig config) {
        try {
            config.setUserId(CmsUtils.getUserId());
            configService.add(config);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    @PostMapping("/getModels")
    @ResponseBody
    @Operation(summary = "获取模型列表", description = "返回模型列表")
    public ResultMessage getModels(SysConfig config) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + config.getApiKey());

            // 构建请求实体
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 调用 /v1/models 接口，解析为 JSON 字符串
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl() + "/models",
                    HttpMethod.GET,
                    entity,
                    String.class);

            // 使用 ObjectMapper 解析 JSON 响应
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            // 提取 "data" 字段
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                return ResultMessage.error("响应数据格式错误，缺少 data 字段或 data 不是数组");
            }

            // 将 "data" 字段解析为 List<Map<String, Object>>
            List<Map<String, Object>> modelList = objectMapper.convertValue(
                    dataNode,
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            // 返回成功结果
            ResultMessage result = ResultMessage.success();
            result.put("data", modelList);
            return result;

        } catch (HttpClientErrorException e) {
            // 捕获 HTTP 客户端异常并返回详细错误信息
            String errorMessage = e.getResponseBodyAsString();
            // 返回详细错误信息到前端
            return ResultMessage.error("调用模型接口失败: " + errorMessage);

        } catch (Exception e) {
            // 捕获其他异常并记录日志
            return ResultMessage.error();
        }
    }
}