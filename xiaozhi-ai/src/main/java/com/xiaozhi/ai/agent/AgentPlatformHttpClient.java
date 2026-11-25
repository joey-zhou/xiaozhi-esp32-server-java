package com.xiaozhi.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.model.bo.PlatformAgentBO;
import com.xiaozhi.common.port.AgentPlatformClient;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * coze 与 dify 的智能体查询接口
 */
@Component
public class AgentPlatformHttpClient implements AgentPlatformClient {

    private static final String COZE_API = "https://api.coze.cn";

    /** 单次请求的整体上限，同步智能体列表的调用方只等 3 秒，超出的部分在后台继续 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String cozeApi;

    public AgentPlatformHttpClient() {
        this(COZE_API);
    }

    AgentPlatformHttpClient(String cozeApi) {
        this.cozeApi = cozeApi;
    }

    @Override
    public List<PlatformAgentBO> listCozeBots(String spaceId, String token) {
        JsonNode root = get(cozeApi + "/v1/space/published_bots_list?space_id="
                + URLEncoder.encode(spaceId, StandardCharsets.UTF_8), token, "Coze 智能体列表");
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("查询Coze智能体列表失败：" + root.path("msg").asText("未知错误"));
        }
        List<PlatformAgentBO> bots = new ArrayList<>();
        for (JsonNode bot : root.path("data").path("space_bots")) {
            bots.add(new PlatformAgentBO(bot.path("bot_id").asText(), bot.path("bot_name").asText(),
                    bot.path("description").asText(), bot.path("icon_url").asText(), publishTimeOf(bot)));
        }
        return bots;
    }

    @Override
    public PlatformAgentBO difyInfo(String apiUrl, String apiKey) {
        JsonNode info = get(apiUrl + "/info", apiKey, "DIFY 应用信息");
        return new PlatformAgentBO(null, textOrNull(info, "name"), textOrNull(info, "description"), null, null);
    }

    @Override
    public String difyIcon(String apiUrl, String apiKey) {
        JsonNode icon = get(apiUrl + "/meta", apiKey, "DIFY 应用图标").path("tool_icons").path("api_tool");
        return textOrNull(icon, "content");
    }

    private JsonNode get(String url, String bearer, String what) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + bearer)
                            .header("Content-Type", "application/json")
                            .timeout(REQUEST_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("查询" + what + "失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查询" + what + "被中断", e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("查询" + what + "失败，HTTP " + response.statusCode());
        }
        JsonNode root = JsonUtil.fromJson(response.body(), JsonNode.class);
        if (root == null) {
            throw new IllegalStateException("查询" + what + "失败，应答不是 JSON");
        }
        return root;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /** coze 的发布时间是秒级时间戳，缺失或非法时留空 */
    private static LocalDateTime publishTimeOf(JsonNode bot) {
        String publishTime = bot.path("publish_time").asText();
        if (!StringUtils.hasText(publishTime)) {
            return null;
        }
        try {
            return DateUtils.toDateTime(Instant.ofEpochSecond(Long.parseLong(publishTime)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
