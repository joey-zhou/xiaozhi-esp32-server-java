package com.xiaozhi.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.agent.convert.AgentConvert;
import com.xiaozhi.agent.service.AgentService;
import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.infrastructure.convert.ConfigConverter;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.token.TokenService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentServiceImpl.class);

    @Resource
    private ConfigService configService;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ConfigConverter configConverter;

    @Resource
    private TokenService tokenService;

    @Resource
    private AgentConvert agentConvert;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResp<AgentResp> page(int pageNo, int pageSize, String provider, String agentName, Integer userId) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase();
        List<AgentBO> agentList = switch (normalizedProvider) {
            case "coze" -> getCozeAgents(agentName, userId);
            case "dify" -> getDifyAgents(agentName, userId);
            case "xingchen" -> getXingChenAgents(agentName, userId);
            default -> List.of();
        };

        int total = agentList.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<AgentResp> list = agentList.subList(fromIndex, toIndex).stream()
            .map(agentConvert::toResp)
            .toList();
        return new PageResp<>(list, (long) total, pageNo, pageSize);
    }

    private List<AgentBO> getDifyAgents(String agentName, Integer userId) {
        List<ConfigBO> allConfigs = configService.listBO(
            userId,
            null,
            "dify",
            null,
            null,
            ConfigBO.STATE_ENABLED);
        if (allConfigs.isEmpty()) {
            return List.of();
        }

        List<ConfigBO> agentConfigs = allConfigs.stream()
            .filter(config -> "agent".equals(config.getConfigType()))
            .toList();
        Map<String, ConfigBO> llmConfigMap = allConfigs.stream()
            .filter(config -> "llm".equals(config.getConfigType()))
            .filter(config -> StringUtils.hasText(config.getApiKey()))
            .collect(HashMap::new, (map, config) -> map.put(config.getApiKey(), config), HashMap::putAll);

        List<AgentBO> result = new ArrayList<>();
        for (ConfigBO agentConfig : agentConfigs) {
            String apiKey = agentConfig.getApiKey();
            String apiUrl = agentConfig.getApiUrl();
            ConfigBO existingLlmConfig = llmConfigMap.get(apiKey);
            if (existingLlmConfig != null) {
                AgentBO agent = new AgentBO();
                fillAgentConfig(agent, existingLlmConfig);
                agent.setAgentName(existingLlmConfig.getConfigName());
                agent.setAgentDesc(existingLlmConfig.getConfigDesc());
                agent.setPublishTime(existingLlmConfig.getCreateTime() == null
                    ? null : java.sql.Timestamp.valueOf(existingLlmConfig.getCreateTime()));
                if (!StringUtils.hasText(agentName)
                    || (StringUtils.hasText(agent.getAgentName())
                    && agent.getAgentName().toLowerCase().contains(agentName.toLowerCase()))) {
                    result.add(agent);
                }
                continue;
            }

            AgentBO agent = new AgentBO();
            fillAgentConfig(agent, agentConfig);
            agent.setAgentName(agentConfig.getConfigName());
            agent.setAgentDesc(agentConfig.getConfigDesc());

            try {
                HttpResponse<String> infoResponse = httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + "/info"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString());

                if (infoResponse.statusCode() == 200) {
                    JsonNode infoNode = objectMapper.readTree(infoResponse.body());
                    String name = infoNode.has("name") ? infoNode.get("name").asText() : "DIFY Agent";
                    String description = infoNode.has("description") ? infoNode.get("description").asText() : "";
                    agent.setAgentName(name);
                    agent.setAgentDesc(description);

                    try {
                        AiConfig aiConfig = AiConfig.newConfig(userId, new ConfigBO()
                            .setConfigType("llm")
                            .setProvider("dify")
                            .setApiKey(apiKey)
                            .setConfigName(name)
                            .setConfigDesc(description)
                            .setApiUrl(apiUrl));
                        configRepository.save(aiConfig);
                        ConfigBO savedConfig = configConverter.toBO(aiConfig);
                        agent = new AgentBO();
                        fillAgentConfig(agent, savedConfig);
                        agent.setAgentName(name);
                        agent.setAgentDesc(description);
                        agent.setPublishTime(savedConfig.getCreateTime() == null
                            ? null : java.sql.Timestamp.valueOf(savedConfig.getCreateTime()));
                        logger.debug("添加DIFY LLM配置成功: {}", apiKey);
                    } catch (RuntimeException e) {
                        logger.warn("同步DIFY智能体配置失败，apiKey={}", apiKey, e);
                    }

                    fillDifyIcon(apiUrl, apiKey, agent);
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.error("查询DIFY智能体信息异常", e);
                agent.setAgentName(StringUtils.hasText(agentConfig.getConfigName()) ? agentConfig.getConfigName() : "DIFY Agent");
                agent.setAgentDesc("无法连接到DIFY API");
            }

            if (!StringUtils.hasText(agentName)
                || (StringUtils.hasText(agent.getAgentName())
                && agent.getAgentName().toLowerCase().contains(agentName.toLowerCase()))) {
                result.add(agent);
            }
        }
        return result;
    }

    private List<AgentBO> getXingChenAgents(String agentName, Integer userId) {
        List<ConfigBO> allConfigs = configService.listBO(
            userId,
            null,
            "xingchen",
            null,
            null,
            ConfigBO.STATE_ENABLED);
        if (allConfigs.isEmpty()) {
            return List.of();
        }

        List<ConfigBO> agentConfigs = allConfigs.stream()
            .filter(config -> "agent".equals(config.getConfigType()))
            .toList();
        Map<String, ConfigBO> llmConfigMap = allConfigs.stream()
            .filter(config -> "llm".equals(config.getConfigType()))
            .filter(config -> StringUtils.hasText(config.getApiKey()))
            .collect(HashMap::new, (map, config) -> map.put(config.getApiKey(), config), HashMap::putAll);

        List<AgentBO> result = new ArrayList<>();
        for (ConfigBO agentConfig : agentConfigs) {
            String apiKey = agentConfig.getApiKey();
            String apiUrl = agentConfig.getApiUrl();
            ConfigBO existingLlmConfig = llmConfigMap.get(apiKey);
            if (existingLlmConfig != null) {
                AgentBO agent = new AgentBO();
                fillAgentConfig(agent, existingLlmConfig);
                agent.setAgentName(existingLlmConfig.getConfigName());
                agent.setAgentDesc(existingLlmConfig.getConfigDesc());
                agent.setPublishTime(existingLlmConfig.getCreateTime() == null
                    ? null : java.sql.Timestamp.valueOf(existingLlmConfig.getCreateTime()));
                if (!StringUtils.hasText(agentName)
                    || (StringUtils.hasText(agent.getAgentName())
                    && agent.getAgentName().toLowerCase().contains(agentName.toLowerCase()))) {
                    result.add(agent);
                }
                continue;
            }

            String name = "XingChen Agent";
            String description = "";
            AgentBO agent = new AgentBO();
            try {
                AiConfig aiConfig = AiConfig.newConfig(userId, new ConfigBO()
                    .setConfigType("llm")
                    .setProvider("xingchen")
                    .setApiKey(apiKey)
                    .setConfigName(name)
                    .setConfigDesc(description)
                    .setApiUrl(apiUrl));
                configRepository.save(aiConfig);
                ConfigBO savedConfig = configConverter.toBO(aiConfig);
                fillAgentConfig(agent, savedConfig);
                agent.setPublishTime(savedConfig.getCreateTime() != null
                    ? java.sql.Timestamp.valueOf(savedConfig.getCreateTime()) : null);
                logger.debug("添加XingChen LLM配置成功: {}", apiKey);
            } catch (RuntimeException e) {
                logger.warn("同步XingChen智能体配置失败，apiKey={}", apiKey, e);
                fillAgentConfig(agent, agentConfig);
            }
            agent.setAgentName(name);
            agent.setAgentDesc(description);
            if (!StringUtils.hasText(agentName)
                || (StringUtils.hasText(agent.getAgentName())
                && agent.getAgentName().toLowerCase().contains(agentName.toLowerCase()))) {
                result.add(agent);
            }
        }
        return result;
    }

    private List<AgentBO> getCozeAgents(String agentName, Integer userId) {
        List<ConfigBO> configs = configService.listBO(
            userId,
            "agent",
            "coze",
            null,
            null,
            ConfigBO.STATE_ENABLED);
        if (configs.isEmpty()) {
            return List.of();
        }

        ConfigBO config = configs.getFirst();
        String spaceId = config.getApiSecret();
        List<AgentBO> result = new ArrayList<>();

        try {
            String token = tokenService.getToken(config);
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coze.cn/v1/space/published_bots_list?space_id=" + spaceId))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return result;
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            if (!rootNode.has("code") || rootNode.get("code").asInt() != 0) {
                String errorMsg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                logger.error("查询Coze智能体列表失败：{}", errorMsg);
                return result;
            }

            List<ConfigBO> existingConfigs = configService.listBO(
                userId,
                "llm",
                "coze",
                null,
                null,
                ConfigBO.STATE_ENABLED);
            Map<String, ConfigBO> existingConfigMap = existingConfigs.stream()
                .filter(existingConfig -> StringUtils.hasText(existingConfig.getConfigName()))
                .collect(HashMap::new, (map, existingConfig) -> map.put(existingConfig.getConfigName(), existingConfig), HashMap::putAll);

            for (JsonNode botNode : rootNode.path("data").path("space_bots")) {
                String botId = botNode.path("bot_id").asText();
                String botName = botNode.path("bot_name").asText();
                String description = botNode.path("description").asText();
                String iconUrl = botNode.path("icon_url").asText();
                long publishTime = Long.parseLong(botNode.path("publish_time").asText());

                AgentBO agent = new AgentBO();
                agent.setBotId(botId);
                agent.setAgentName(botName);
                agent.setAgentDesc(description);
                agent.setIconUrl(iconUrl);
                agent.setPublishTime(new Date(publishTime * 1000));
                agent.setProvider("coze");

                ConfigBO existingConfig = existingConfigMap.get(botId);
                if (existingConfig != null) {
                    existingConfig.setConfigName(botId);
                    existingConfig.setConfigDesc(description);
                    try {
                        AiConfig aiConfig = configRepository.findById(existingConfig.getConfigId())
                            .orElseThrow(() -> new RuntimeException("配置不存在: " + existingConfig.getConfigId()));
                        aiConfig.update(existingConfig);
                        configRepository.save(aiConfig);
                        fillAgentConfig(agent, configConverter.toBO(aiConfig));
                    } catch (RuntimeException e) {
                        logger.warn("同步Coze智能体配置失败，botId={}", botId, e);
                        fillAgentConfig(agent, existingConfig);
                    }
                } else {
                    try {
                        AiConfig aiConfig = AiConfig.newConfig(userId, new ConfigBO()
                            .setConfigType("llm")
                            .setProvider("coze")
                            .setConfigName(botId)
                            .setConfigDesc(description));
                        configRepository.save(aiConfig);
                        fillAgentConfig(agent, configConverter.toBO(aiConfig));
                    } catch (RuntimeException e) {
                        logger.warn("创建Coze智能体配置失败，botId={}", botId, e);
                    }
                }

                if (!StringUtils.hasText(agentName)
                    || (StringUtils.hasText(agent.getAgentName())
                    && agent.getAgentName().toLowerCase().contains(agentName.toLowerCase()))) {
                    result.add(agent);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("查询Coze智能体列表异常", e);
        } catch (IOException e) {
            logger.error("查询Coze智能体列表异常", e);
        } catch (RuntimeException e) {
            logger.error("获取Coze Token失败", e);
            throw new RuntimeException("无法获取Coze平台授权码，请检查您的平台配置是否正确", e);
        }

        return result;
    }

    private void fillAgentConfig(AgentBO agent, ConfigBO config) {
        if (agent == null || config == null) {
            return;
        }
        agent.setConfigId(config.getConfigId());
        agent.setUserId(config.getUserId());
        agent.setConfigName(config.getConfigName());
        agent.setConfigDesc(config.getConfigDesc());
        agent.setConfigType(config.getConfigType());
        agent.setModelType(config.getModelType());
        agent.setProvider(config.getProvider());
        agent.setAppId(config.getAppId());
        agent.setApiUrl(config.getApiUrl());
        agent.setState(config.getState());
        agent.setIsDefault(config.getIsDefault());
        agent.setCreateTime(config.getCreateTime());
        agent.setUpdateTime(config.getUpdateTime());
    }

    private void fillDifyIcon(String apiUrl, String apiKey, AgentBO agent) {
        try {
            HttpResponse<String> metaResponse = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/meta"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            if (metaResponse.statusCode() != 200) {
                return;
            }

            JsonNode metaNode = objectMapper.readTree(metaResponse.body());
            if (metaNode.has("tool_icons") && metaNode.get("tool_icons").has("api_tool")) {
                JsonNode apiTool = metaNode.get("tool_icons").get("api_tool");
                if (apiTool.has("content")) {
                    agent.setIconUrl(apiTool.get("content").asText());
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("获取DIFY meta信息异常", e);
        }
    }
}
