package com.xiaozhi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.entity.SysAgent;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysAgentService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能体服务实现
 * 
 * @author Joey
 */
@Service
public class SysAgentServiceImpl implements SysAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SysAgentServiceImpl.class);

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private TokenServiceFactory tokenService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 添加智能体
     * 
     * @param agent 智能体信息
     * @return 结果
     */
    @Override
    @Transactional
    public int add(SysAgent agent) {
        return 0;
    }

    /**
     * 修改智能体
     * 
     * @param agent 智能体信息
     * @return 结果
     */
    @Override
    @Transactional
    public int update(SysAgent agent) {
        return 0;
    }

    /**
     * 删除智能体
     * 
     * @param agentId 智能体ID
     * @return 结果
     */
    @Override
    @Transactional
    public int delete(Integer agentId) {
        return 0;
    }

    /**
     * 查询智能体列表 - 同步版本
     * 
     * @param agent 智能体信息
     * @return 智能体集合
     */
    @Override
    public List<SysAgent> query(SysAgent agent) {
        // 如果设置了平台为Coze，则从Coze API获取智能体列表
        if ("coze".equalsIgnoreCase(agent.getProvider())) {
            return getCozeAgents(agent);
        } else if ("dify".equalsIgnoreCase(agent.getProvider())) {
            // 如果是DIFY平台，则从DIFY API获取智能体列表
            return getDifyAgents(agent);
        } else {
            // 如果不是特定平台，返回空
            return new ArrayList<>();
        }
    }

    /**
     * 从DIFY API获取智能体信息，并与数据库同步
     * 
     * @param agent 智能体信息
     * @return 智能体集合
     */
    private List<SysAgent> getDifyAgents(SysAgent agent) {
        List<SysAgent> agentList = new ArrayList<>();
        
        // 查询所有类型的Dify配置
        SysConfig queryConfig = new SysConfig();
        queryConfig.setProvider("dify");
        List<SysConfig> allConfigs = configMapper.query(queryConfig);
        
        if (ObjectUtils.isEmpty(allConfigs)) {
            return agentList;
        }

        List<SysConfig> agentConfigs = allConfigs.stream()
                .filter(config -> "agent".equals(config.getConfigType()))
                .collect(Collectors.toList());

        // 创建一个Map来存储llm配置，以apiKey为键
        Map<String, SysConfig> llmConfigMap = new HashMap<>();
        allConfigs.stream()
                .filter(config -> "llm".equals(config.getConfigType()))
                .forEach(config -> {
                    if (config.getApiKey() != null) {
                        llmConfigMap.put(config.getApiKey(), config);
                    }
                });
        
        // 处理每个agent配置
        for (SysConfig agentConfig : agentConfigs) {
            String apiKey = agentConfig.getApiKey();
            String apiUrl = agentConfig.getApiUrl();
            Integer configId = agentConfig.getConfigId();
            Integer userId = agentConfig.getUserId();

            
            // 检查是否已存在对应的llm配置
            SysConfig existingLlmConfig = llmConfigMap.get(apiKey);
            
            // 如果已存在llm配置，直接创建Agent对象返回
            if (existingLlmConfig != null) {
                SysAgent difyAgent = new SysAgent();
                difyAgent.setConfigId(existingLlmConfig.getConfigId());
                difyAgent.setProvider("dify");
                difyAgent.setApiKey(apiKey);
                difyAgent.setAgentName(existingLlmConfig.getConfigName());
                difyAgent.setAgentDesc(existingLlmConfig.getConfigDesc());
                difyAgent.setIsDefault(existingLlmConfig.getIsDefault());
                difyAgent.setPublishTime(existingLlmConfig.getCreateTime());
                
                // 如果前端传入了智能体名称过滤条件，则进行过滤
                if (StringUtils.hasText(agent.getAgentName())) {
                    if (difyAgent.getAgentName() != null && 
                        difyAgent.getAgentName().toLowerCase().contains(agent.getAgentName().toLowerCase())) {
                        agentList.add(difyAgent);
                    }
                } else {
                    agentList.add(difyAgent);
                }
            } else {
                // 如果不存在llm配置，调用API获取信息并创建新的llm配置
                SysAgent difyAgent = new SysAgent();
                difyAgent.setConfigId(configId);
                difyAgent.setProvider("dify");
                difyAgent.setApiKey(apiKey);
                
                try {
                    // 调用info API
                    HttpRequest infoRequest = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl + "/info"))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .GET()
                            .build();
                    
                    HttpResponse<String> infoResponse = httpClient.send(infoRequest, 
                            HttpResponse.BodyHandlers.ofString());
                    
                    if (infoResponse.statusCode() == 200) {
                        JsonNode infoNode = objectMapper.readTree(infoResponse.body());
                        String name = infoNode.has("name") ? infoNode.get("name").asText() : "DIFY Agent";
                        String description = infoNode.has("description") ? infoNode.get("description").asText() : "";
                        
                        difyAgent.setAgentName(name);
                        difyAgent.setAgentDesc(description);
                        
                        // 创建新的llm配置
                        SysConfig newLlmConfig = new SysConfig();
                        newLlmConfig.setUserId(userId);
                        newLlmConfig.setConfigType("llm");
                        newLlmConfig.setProvider("dify");
                        newLlmConfig.setApiKey(apiKey);
                        newLlmConfig.setConfigName(name);
                        newLlmConfig.setConfigDesc(description);
                        newLlmConfig.setApiUrl(apiUrl);
                        newLlmConfig.setState(SysDevice.DEVICE_STATE_ONLINE);  // 默认启用
                        
                        // 添加到数据库
                        try {
                            configMapper.add(newLlmConfig);
                            logger.debug("添加DIFY LLM配置成功: {}", apiKey);
                            difyAgent.setConfigId(newLlmConfig.getConfigId());
                        } catch (Exception e) {
                            logger.error("添加DIFY LLM配置失败: {}", e.getMessage());
                        }
                        
                        // 获取图标信息
                        try {
                            HttpRequest metaRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(apiUrl + "/meta"))
                                    .header("Authorization", "Bearer " + apiKey)
                                    .header("Content-Type", "application/json")
                                    .GET()
                                    .build();
                            
                            HttpResponse<String> metaResponse = httpClient.send(metaRequest, 
                                    HttpResponse.BodyHandlers.ofString());
                            
                            if (metaResponse.statusCode() == 200) {
                                JsonNode metaNode = objectMapper.readTree(metaResponse.body());
                                if (metaNode.has("tool_icons") && metaNode.get("tool_icons").has("api_tool")) {
                                    JsonNode apiTool = metaNode.get("tool_icons").get("api_tool");
                                    if (apiTool.has("content")) {
                                        String iconContent = apiTool.get("content").asText();
                                        difyAgent.setIconUrl(iconContent);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error("获取DIFY meta信息异常", e);
                        }
                    }
                } catch (Exception e) {
                    logger.error("查询DIFY智能体信息异常", e);
                    difyAgent.setAgentName(agentConfig.getConfigName() != null ? agentConfig.getConfigName() : "DIFY Agent");
                    difyAgent.setAgentDesc("无法连接到DIFY API");
                }
                
                // 如果前端传入了智能体名称过滤条件，则进行过滤
                if (StringUtils.hasText(agent.getAgentName())) {
                    if (difyAgent.getAgentName() != null && 
                        difyAgent.getAgentName().toLowerCase().contains(agent.getAgentName().toLowerCase())) {
                        agentList.add(difyAgent);
                    }
                } else {
                    agentList.add(difyAgent);
                }
            }
        }
        
        return agentList;
    }

    /**
     * 从Coze API获取智能体列表，并与数据库同步
     * 
     * @param agent 智能体信息
     * @return 智能体集合
     */
    private List<SysAgent> getCozeAgents(SysAgent agent) {
        List<SysAgent> agentList = new ArrayList<>();

        // 获取当前用户的Coze配置
        List<SysConfig> configs = configMapper.query(agent);
        if (ObjectUtils.isEmpty(configs)) {
            return agentList;
        }

        SysConfig config = configs.get(0);

        String spaceId = config.getApiSecret();

        // 普通用户应该只能查询使用管理员配置的内容
        Integer userId = config.getUserId();


        try {
            String token = tokenService.getTokenService(config).getToken();
            // 调用Coze API获取智能体列表
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coze.cn/v1/space/published_bots_list?space_id=" + spaceId))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode rootNode = objectMapper.readTree(response.body());
                if (rootNode.has("code") && rootNode.get("code").asInt() == 0) {
                    JsonNode spaceBots = rootNode.path("data").path("space_bots");
                    
                    // 查询数据库中现有的所有与当前用户相关的coze智能体配置
                    SysConfig queryConfig = new SysConfig();
                    queryConfig.setUserId(userId);
                    queryConfig.setConfigType("llm");
                    queryConfig.setProvider("coze");
                    List<SysConfig> existingConfigs = configMapper.query(queryConfig);
                    
                    // 创建一个Map来存储现有的配置，以botId为键
                    Map<String, SysConfig> existingConfigMap = new HashMap<>();
                    for (SysConfig existingConfig : existingConfigs) {
                        if (existingConfig.getConfigName() != null) {
                            existingConfigMap.put(existingConfig.getConfigName(), existingConfig);
                        }
                    }

                    // 记录API返回的所有botId，用于后续比对删除
                    List<String> apiBotIds = new ArrayList<>();

                    // 遍历智能体列表
                    for (JsonNode botNode : spaceBots) {
                        String botId = botNode.path("bot_id").asText();
                        String botName = botNode.path("bot_name").asText();
                        String description = botNode.path("description").asText();
                        String iconUrl = botNode.path("icon_url").asText();
                        long publishTime = Long.parseLong(botNode.path("publish_time").asText());
                        
                        apiBotIds.add(botId);
                        
                        // 创建SysAgent对象用于返回
                        SysAgent botAgent = new SysAgent();
                        botAgent.setBotId(botId);
                        botAgent.setAgentName(botName);
                        botAgent.setAgentDesc(description);
                        botAgent.setIconUrl(iconUrl);
                        botAgent.setPublishTime(new Date(publishTime * 1000));
                        botAgent.setProvider("coze");
                        
                        // 同步到数据库
                        // 检查是否已存在该botId的配置
                        if (existingConfigMap.containsKey(botId)) {
                            // 存在则更新
                            SysConfig existingConfig = existingConfigMap.get(botId);
                            existingConfig.setConfigName(botId);
                            existingConfig.setConfigDesc(description);
                            // 如果数据库已存在，返回对应 ConfigId 为前端设备绑定使用
                            botAgent.setConfigId(existingConfig.getConfigId());
                            botAgent.setIsDefault(existingConfig.getIsDefault());

                            // 更新配置
                            try {
                                configMapper.update(existingConfig);
                            } catch (Exception e) {
                                logger.error("更新智能体配置失败: {}", e.getMessage());
                            }
                        } else {
                            // 不存在则新增
                            SysConfig newConfig = new SysConfig();
                            newConfig.setUserId(userId);
                            newConfig.setConfigType("llm");
                            newConfig.setProvider("coze");
                            newConfig.setConfigName(botId);
                            newConfig.setConfigDesc(description);
                            newConfig.setState(SysDevice.DEVICE_STATE_ONLINE);  // 默认启用
                            configMapper.add(newConfig);
                        }

                        // 如果前端传入了智能体名称过滤条件，则进行过滤
                        if (StringUtils.hasText(agent.getAgentName())) {
                            if (botAgent.getAgentName().toLowerCase()
                                    .contains(agent.getAgentName().toLowerCase())) {
                                agentList.add(botAgent);
                            }
                        } else {
                            agentList.add(botAgent);
                        }
                    }
                    
                    // 删除不再存在的智能体配置 (暂时注释掉，与原代码保持一致)
                    // for (String existingBotId : existingConfigMap.keySet()) {
                    //     if (!apiBotIds.contains(existingBotId)) {
                    //         SysConfig configToDelete = existingConfigMap.get(existingBotId);
                    //         try {
                    //             configMapper.delete(configToDelete.getConfigId());
                    //             logger.debug("删除智能体配置成功: {}", existingBotId);
                    //         } catch (Exception e) {
                    //             logger.error("删除智能体配置失败: {}", e.getMessage());
                    //         }
                    //     }
                    // }
                } else {
                    String errorMsg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                    logger.error("查询Coze智能体列表失败：{}", errorMsg);
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.error("查询Coze智能体列表异常", e);
        } catch (RuntimeException e) {
            logger.error("获取Coze Token失败", e);
            throw new RuntimeException("无法获取Coze平台授权码，请检查您的平台配置是否正确", e);
        }
        
        return agentList;
    }
}