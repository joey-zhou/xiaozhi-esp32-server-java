package com.xiaozhi.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.agent.AgentProviders;
import com.xiaozhi.agent.convert.AgentConvert;
import com.xiaozhi.agent.service.AgentService;
import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 智能体列表。
 * <p>
 * 第三方平台上的一个智能体，在本系统里就是一条 llm 配置（coze 用 configName 存 botId，
 * dify、xingchen 一份凭据对应一个智能体、按 apiKey 认）。列表本身只读这些已落库的配置：
 * 请求线程里既不写库也不发平台请求，翻页与搜索的耗时只取决于本地查询。
 * <p>
 * 与平台对齐（发现新智能体就补一条配置、描述变了就更新）交给后台任务，按「用户+平台」节流，
 * 列表访问频率不再等于对平台的调用频率。名称、头像、发布时间这类 sys_config 没有列可存的字段，
 * 由上一轮同步缓存在进程内，跟着列表一起返回。
 * <p>
 * 两种情况下列表会在预算内等这一次同步：库里一条都没有（多半刚配好平台凭据，这一次还不受节流限制），
 * 以及进程内还没有平台侧快照（首次访问或刚重启，不等的话 coze 的名称与头像会先空一轮）。
 * 等不到就先返回库里已有的内容，同步在后台继续，下次访问即可看到。
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private static final String PROVIDER_COZE = AgentProviders.COZE;
    private static final String PROVIDER_DIFY = AgentProviders.DIFY;
    private static final String PROVIDER_XINGCHEN = AgentProviders.XINGCHEN;
    private static final Set<String> SUPPORTED_PROVIDERS = AgentProviders.ALL;

    /** 智能体落库后的配置类型 */
    private static final String MODEL_CONFIG_TYPE = "llm";
    /** 平台凭据（appId、apiKey、空间 ID 等）另存一条配置，只供同步使用 */
    private static final String CREDENTIAL_CONFIG_TYPE = "agent";

    private static final String DIFY_DEFAULT_NAME = "DIFY Agent";
    private static final String XINGCHEN_DEFAULT_NAME = "XingChen Agent";

    /**
     * 同一用户同一平台两次同步的最小间隔。平台上的智能体是低频变更，不设间隔的话
     * 对平台的调用量直接随列表访问频率线性增长。
     */
    private static final Duration SYNC_INTERVAL = Duration.ofSeconds(30);

    /** 列表接口等同步的上限。平台变慢时宁可这一轮少几个平台侧字段，也不能让列表接口跟着卡住 */
    private static final Duration SYNC_WAIT_BUDGET = Duration.ofSeconds(3);

    /** 第三方平台单次请求的整体超时，超时抛 HttpTimeoutException 由各分支降级。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final ExecutorService SYNC_EXECUTOR = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("agent-sync-", 0).factory());

    @Resource
    private ConfigService configService;

    @Resource
    private ProviderTokenClient tokenClient;

    @Resource
    private AgentConvert agentConvert;

    /** userId:provider -> 上次向平台同步的时刻（毫秒） */
    private final Map<String, Long> lastSyncAt = new ConcurrentHashMap<>();

    /** userId:provider -> 上一轮同步拿到的平台侧字段，键是 botId(coze) 或 apiKey(dify、xingchen) */
    private final Map<String, Map<String, RemoteAgent>> remoteSnapshots = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 平台侧才有、sys_config 没有列可存的字段 */
    private record RemoteAgent(String name, String iconUrl, LocalDateTime publishTime) {}

    @Override
    public PageResult<AgentBO> page(int pageNo, int pageSize, String provider, String agentName, Integer userId) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(normalizedProvider)) {
            return new PageResult<>(List.of(), 0L, pageNo, pageSize);
        }

        String snapshotKey = snapshotKey(userId, normalizedProvider);
        List<AgentBO> stored = listStoredAgents(normalizedProvider, userId, snapshotKey);
        // 库里一条都没有，多半是刚配好平台凭据还没同步过，这一次不受节流限制，否则用户对着空列表干等；
        // 进程内还没有平台侧快照（首次访问或刚重启）也等一次，否则 coze 的名称与头像会先空一轮
        boolean firstLoad = stored.isEmpty();
        boolean waitForSync = firstLoad || !remoteSnapshots.containsKey(snapshotKey);
        CompletableFuture<Void> sync = syncAsync(normalizedProvider, userId, snapshotKey, firstLoad);
        if (sync != null && waitForSync) {
            awaitSync(sync, normalizedProvider);
            stored = listStoredAgents(normalizedProvider, userId, snapshotKey);
        }

        List<AgentBO> agentList = stored.stream()
            .filter(agent -> matchesName(agent, agentName))
            .toList();
        int total = agentList.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        return new PageResult<>(List.copyOf(agentList.subList(fromIndex, toIndex)),
            (long) total, pageNo, pageSize);
    }

    /** 库里存得下的字段一律读库，剩下的从上一轮同步的快照里补 */
    private List<AgentBO> listStoredAgents(String provider, Integer userId, String snapshotKey) {
        List<ConfigBO> models = configService.listBO(
            userId, MODEL_CONFIG_TYPE, provider, null, null, ConfigBO.STATE_ENABLED);
        Map<String, RemoteAgent> remote = remoteSnapshots.getOrDefault(snapshotKey, Map.of());

        List<AgentBO> result = new ArrayList<>();
        for (ConfigBO model : models) {
            String remoteKey = remoteKey(provider, model);
            result.add(toAgent(provider, model, remoteKey == null ? null : remote.get(remoteKey)));
        }
        return result;
    }

    private AgentBO toAgent(String provider, ConfigBO model, RemoteAgent remote) {
        AgentBO agent = agentConvert.toBO(model);
        if (PROVIDER_COZE.equals(provider)) {
            // coze 的 llm 配置用 configName 存 botId，对话时也是按它路由到平台上的智能体
            agent.setBotId(model.getConfigName());
        }
        if (remote == null) {
            return agent;
        }
        if (StringUtils.hasText(remote.name())) {
            agent.setAgentName(remote.name());
        }
        if (StringUtils.hasText(remote.iconUrl())) {
            agent.setIconUrl(remote.iconUrl());
        }
        if (remote.publishTime() != null) {
            agent.setPublishTime(remote.publishTime());
        }
        return agent;
    }

    /** coze 一个智能体一条配置、按 botId 认；dify、xingchen 一份凭据一个智能体、按 apiKey 认 */
    private static String remoteKey(String provider, ConfigBO model) {
        return PROVIDER_COZE.equals(provider) ? model.getConfigName() : model.getApiKey();
    }

    private static boolean matchesName(AgentBO agent, String agentName) {
        if (!StringUtils.hasText(agentName)) {
            return true;
        }
        return StringUtils.hasText(agent.getAgentName())
            && agent.getAgentName().toLowerCase().contains(agentName.toLowerCase());
    }

    private static String snapshotKey(Integer userId, String provider) {
        return userId + ":" + provider;
    }

    /**
     * 触发一次与平台的同步，距上次不足 {@link #SYNC_INTERVAL} 则跳过并返回 null。
     * 时间戳在这里就先占上，并发的多次访问只会放行第一次。
     *
     * @param force 库里还没有这个平台的智能体时为 true，此时不受节流限制
     */
    private CompletableFuture<Void> syncAsync(String provider, Integer userId, String snapshotKey, boolean force) {
        long now = DateUtils.millis();
        if (force) {
            lastSyncAt.put(snapshotKey, now);
        } else {
            Long previous = lastSyncAt.merge(snapshotKey, now,
                (old, fresh) -> fresh - old < SYNC_INTERVAL.toMillis() ? old : fresh);
            if (previous != now) {
                return null;
            }
        }
        return CompletableFuture.runAsync(() -> sync(provider, userId, snapshotKey), SYNC_EXECUTOR)
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    log.error("同步{}智能体失败", provider, error);
                }
            });
    }

    /**
     * 等不到就先返回库里已有的内容，同步在后台继续；平台凭据不可用这类错误照旧抛给调用方，
     * 与改造前列表接口的行为一致。
     */
    private void awaitSync(CompletableFuture<Void> sync, String provider) {
        try {
            sync.get(SYNC_WAIT_BUDGET.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("同步{}智能体超时，本轮先返回库中已有的智能体", provider);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("同步智能体失败", e.getCause());
        }
    }

    private void sync(String provider, Integer userId, String snapshotKey) {
        Map<String, RemoteAgent> snapshot = switch (provider) {
            case PROVIDER_COZE -> syncCoze(userId);
            case PROVIDER_DIFY -> syncDify(userId);
            case PROVIDER_XINGCHEN -> syncXingChen(userId);
            default -> Map.of();
        };
        // 平台这一轮没答复（null）时保留上一轮的快照，别把已经显示出来的名称与头像抹掉
        if (snapshot != null) {
            remoteSnapshots.put(snapshotKey, snapshot);
        }
    }

    /**
     * dify 一份凭据对应平台上的一个智能体。已落库的不再问平台；新发现的问一次 /info 取名称与描述
     * 落库，再问一次 /meta 取头像（头像库里没列可存，只进快照）。多份凭据并发跑，
     * 逐条串行会把每一次往返都累加到同步耗时上。
     */
    private Map<String, RemoteAgent> syncDify(Integer userId) {
        List<ConfigBO> pending = pendingCredentials(configService.listBO(
            userId, null, PROVIDER_DIFY, null, null, ConfigBO.STATE_ENABLED));
        if (pending.isEmpty()) {
            return Map.of();
        }

        Map<String, RemoteAgent> snapshot = new ConcurrentHashMap<>();
        CompletableFuture.allOf(pending.stream()
                .map(credential -> CompletableFuture.runAsync(
                    () -> materializeDifyAgent(userId, credential, snapshot), SYNC_EXECUTOR))
                .toArray(CompletableFuture[]::new))
            .join();
        return snapshot;
    }

    /** 单份凭据同步失败只记日志，不牵连同一轮里的其它凭据 */
    private void materializeDifyAgent(Integer userId, ConfigBO credential, Map<String, RemoteAgent> snapshot) {
        String apiKey = credential.getApiKey();
        String apiUrl = credential.getApiUrl();
        try {
            JsonNode info = fetchDifyNode(apiUrl, "/info", apiKey);
            if (info == null) {
                return;
            }
            configService.saveAgentModel(new ConfigBO()
                .setUserId(userId)
                .setProvider(PROVIDER_DIFY)
                .setApiKey(apiKey)
                .setApiUrl(apiUrl)
                .setConfigName(info.has("name") ? info.get("name").asText() : DIFY_DEFAULT_NAME)
                .setConfigDesc(info.has("description") ? info.get("description").asText() : ""));

            String iconUrl = fetchDifyIcon(apiUrl, apiKey);
            if (StringUtils.hasText(iconUrl)) {
                snapshot.put(apiKey, new RemoteAgent(null, iconUrl, null));
            }
        } catch (RuntimeException e) {
            log.warn("同步DIFY智能体配置失败，configId={}", credential.getConfigId(), e);
        }
    }

    /** xingchen 没有查询智能体的接口，凭据本身就代表平台上那个智能体，缺配置就补一条 */
    private Map<String, RemoteAgent> syncXingChen(Integer userId) {
        List<ConfigBO> pending = pendingCredentials(configService.listBO(
            userId, null, PROVIDER_XINGCHEN, null, null, ConfigBO.STATE_ENABLED));
        for (ConfigBO credential : pending) {
            try {
                configService.saveAgentModel(new ConfigBO()
                    .setUserId(userId)
                    .setProvider(PROVIDER_XINGCHEN)
                    .setApiKey(credential.getApiKey())
                    .setApiUrl(credential.getApiUrl())
                    .setConfigName(XINGCHEN_DEFAULT_NAME)
                    .setConfigDesc(""));
            } catch (RuntimeException e) {
                log.warn("同步XingChen智能体配置失败，configId={}", credential.getConfigId(), e);
            }
        }
        return Map.of();
    }

    /** 平台凭据里还没落成 llm 配置的那些，就是本轮要补的智能体 */
    private static List<ConfigBO> pendingCredentials(List<ConfigBO> allConfigs) {
        Set<String> storedKeys = allConfigs.stream()
            .filter(config -> MODEL_CONFIG_TYPE.equals(config.getConfigType()))
            .map(ConfigBO::getApiKey)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
        return allConfigs.stream()
            .filter(config -> CREDENTIAL_CONFIG_TYPE.equals(config.getConfigType()))
            .filter(config -> StringUtils.hasText(config.getApiKey()))
            .filter(config -> !storedKeys.contains(config.getApiKey()))
            .toList();
    }

    /**
     * 拉平台上已发布的智能体：没有对应配置的补一条，描述变了的更新一条。
     * 名称、头像、发布时间 sys_config 存不下，只放进本轮快照。
     *
     * @return 平台没答复时返回 null，表示这一轮没有新快照
     */
    private Map<String, RemoteAgent> syncCoze(Integer userId) {
        List<ConfigBO> credentials = configService.listBO(
            userId, CREDENTIAL_CONFIG_TYPE, PROVIDER_COZE, null, null, ConfigBO.STATE_ENABLED);
        if (credentials.isEmpty()) {
            return Map.of();
        }

        ConfigBO credential = credentials.getFirst();
        String token;
        try {
            token = tokenClient.getToken(credential);
        } catch (RuntimeException e) {
            log.error("获取Coze Token失败", e);
            throw new RuntimeException("无法获取Coze平台授权码，请检查您的平台配置是否正确", e);
        }

        JsonNode bots = fetchCozeBots(credential.getApiSecret(), token);
        if (bots == null) {
            return null;
        }

        Map<String, ConfigBO> storedByBotId = new HashMap<>();
        for (ConfigBO stored : configService.listBO(
            userId, MODEL_CONFIG_TYPE, PROVIDER_COZE, null, null, ConfigBO.STATE_ENABLED)) {
            if (StringUtils.hasText(stored.getConfigName())) {
                storedByBotId.put(stored.getConfigName(), stored);
            }
        }

        Map<String, RemoteAgent> snapshot = new HashMap<>();
        for (JsonNode botNode : bots) {
            String botId = botNode.path("bot_id").asText();
            if (!StringUtils.hasText(botId)) {
                continue;
            }
            String description = botNode.path("description").asText();
            snapshot.put(botId, new RemoteAgent(
                botNode.path("bot_name").asText(),
                botNode.path("icon_url").asText(),
                publishTimeOf(botNode)));

            ConfigBO stored = storedByBotId.get(botId);
            try {
                if (stored == null) {
                    // coze 的 llm 配置不带凭据，对话时的 token 取自 agent 那条平台配置
                    configService.saveAgentModel(new ConfigBO()
                        .setUserId(userId)
                        .setProvider(PROVIDER_COZE)
                        .setConfigName(botId)
                        .setConfigDesc(description));
                } else if (!sameText(stored.getConfigDesc(), description)) {
                    // 内容无变化时不得写库。写一次会广播 AiConfigChangedEvent，
                    // 各实例据此清空命中该 llm 配置的在线会话上下文
                    configService.saveAgentModel(stored.setConfigDesc(description));
                }
            } catch (RuntimeException e) {
                log.warn("同步Coze智能体配置失败，botId={}", botId, e);
            }
        }
        return snapshot;
    }

    /** 平台已发布智能体的节点数组，失败返回 null，本轮沿用库里已有的内容 */
    private JsonNode fetchCozeBots(String spaceId, String token) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coze.cn/v1/space/published_bots_list?space_id=" + spaceId))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("查询Coze智能体列表失败，HTTP {}", response.statusCode());
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            if (!rootNode.has("code") || rootNode.get("code").asInt() != 0) {
                log.error("查询Coze智能体列表失败：{}",
                    rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误");
                return null;
            }
            return rootNode.path("data").path("space_bots");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("查询Coze智能体列表异常", e);
            return null;
        } catch (IOException e) {
            log.error("查询Coze智能体列表异常", e);
            return null;
        }
    }

    /** coze 的发布时间是秒级时间戳，缺失或非法时留空，由配置的创建时间兜底 */
    private static LocalDateTime publishTimeOf(JsonNode botNode) {
        String publishTime = botNode.path("publish_time").asText();
        if (!StringUtils.hasText(publishTime)) {
            return null;
        }
        try {
            return DateUtils.toDateTime(Instant.ofEpochSecond(Long.parseLong(publishTime)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 文本比较，null 与空串视为相同。 */
    private static boolean sameText(String left, String right) {
        return Objects.equals(left == null ? "" : left, right == null ? "" : right);
    }

    /** dify 的应用信息接口，失败返回 null */
    private JsonNode fetchDifyNode(String apiUrl, String path, String apiKey) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + path))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("查询DIFY{}失败，HTTP {}", path, response.statusCode());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("查询DIFY{}异常", path, e);
            return null;
        } catch (IOException e) {
            log.error("查询DIFY{}异常", path, e);
            return null;
        }
    }

    private String fetchDifyIcon(String apiUrl, String apiKey) {
        JsonNode metaNode = fetchDifyNode(apiUrl, "/meta", apiKey);
        if (metaNode == null) {
            return null;
        }
        JsonNode apiTool = metaNode.path("tool_icons").path("api_tool");
        return apiTool.has("content") ? apiTool.get("content").asText() : null;
    }
}
