package com.xiaozhi.ai.agent;

import com.sun.net.httpserver.HttpServer;
import com.xiaozhi.common.model.bo.PlatformAgentBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 平台的应答解析成智能体；没答复（HTTP 状态、业务码）一律抛异常，不返回空结果 */
class AgentPlatformHttpClientTest {

    private HttpServer server;
    private final Map<String, String> bodies = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private String lastAuthorization;
    private AgentPlatformHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            String path = exchange.getRequestURI().getPath();
            byte[] body = bodies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 200), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        client = new AgentPlatformHttpClient(baseUrl());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void cozeBotsAreParsed() {
        bodies.put("/v1/space/published_bots_list", """
                {"code":0,"data":{"space_bots":[
                  {"bot_id":"b1","bot_name":"售后客服","description":"处理退换货","icon_url":"https://icon","publish_time":"1700000000"}
                ]}}""");

        PlatformAgentBO bot = client.listCozeBots("space-1", "tk").getFirst();

        assertThat(bot.id()).isEqualTo("b1");
        assertThat(bot.name()).isEqualTo("售后客服");
        assertThat(bot.description()).isEqualTo("处理退换货");
        assertThat(bot.iconUrl()).isEqualTo("https://icon");
        assertThat(bot.publishTime()).isNotNull();
        assertThat(lastAuthorization).isEqualTo("Bearer tk");
    }

    @Test
    void aCozeErrorCodeIsAFailure() {
        bodies.put("/v1/space/published_bots_list", "{\"code\":4100,\"msg\":\"token 无效\"}");

        assertThatThrownBy(() -> client.listCozeBots("space-1", "tk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token 无效");
    }

    @Test
    void anHttpErrorIsAFailure() {
        statuses.put("/v1/info", 502);

        assertThatThrownBy(() -> client.difyInfo(baseUrl() + "/v1", "app-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("502");
    }

    @Test
    void difyInfoAndIconAreParsed() {
        bodies.put("/v1/info", "{\"name\":\"客服助手\",\"description\":\"售后问答\"}");
        bodies.put("/v1/meta", "{\"tool_icons\":{\"api_tool\":{\"content\":\"https://dify-icon\"}}}");

        PlatformAgentBO info = client.difyInfo(baseUrl() + "/v1", "app-key");

        assertThat(info.name()).isEqualTo("客服助手");
        assertThat(info.description()).isEqualTo("售后问答");
        assertThat(lastAuthorization).isEqualTo("Bearer app-key");
        assertThat(client.difyIcon(baseUrl() + "/v1", "app-key")).isEqualTo("https://dify-icon");
    }

    @Test
    void aDifyAppWithoutIconHasNone() {
        assertThat(client.difyIcon(baseUrl() + "/v1", "app-key")).isNull();
    }
}
