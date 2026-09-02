package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.communication.auth.DeviceAuthService;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.domain.DeviceMcpMessage;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpPayload;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉住设备端 MCP 的请求—应答不变量：
 * <ul>
 *   <li>hello 声明 features.mcp 后，服务端必须按 initialize → tools/list 的顺序完成握手，
 *       initialize 带的 vision 能力要能被服务端自己的 token 校验器验回来，
 *       工具以 sanitize 后的名字进 ToolsSessionHolder，mcp_list 持久化的仍是设备上报的原名；</li>
 *   <li>阻塞请求的 pending 表必须先登记再发消息，否则设备秒回的应答会落空；</li>
 *   <li>id 不在 pending 表里的应答必须被安静丢弃，不影响在途请求也不关连接。</li>
 * </ul>
 *
 * <p>脚手架里的 DeviceMcpService 是 mock，这组用例要测它自己，
 * 所以在 setUp 里装一个真的并替换掉 WebSocketHandler 上的那个（不改脚手架文件）。
 *
 * <p>生产代码 DeviceMcpService#sendMcpRequest 是先发消息后登记 pending，
 * 用例里的回包一律等 pending 登记完成后再发，否则会真的干等 30 秒超时。
 */
class DeviceMcpProtocolTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String AUTH_SECRET = "mcp-protocol-test-secret";
    private static final String SERVER_ADDRESS = "http://127.0.0.1:8091";

    /** 设备对 initialize 的应答，字段与真实固件一致 */
    private static final String INITIALIZE_RESULT = """
            {"protocolVersion":"2024-11-05","capabilities":{"tools":{}},
             "serverInfo":{"name":"xiaozhi-device","version":"1.0.0"}}""";

    /**
     * 设备对 tools/list 的应答：工具原名带 '.'，description 里也引用了原名。
     * 故意让一个原名（self.audio_speaker）是另一个原名的前缀，用来钉住长名先替换的顺序。
     */
    private static final String TOOLS_LIST_RESULT = """
            {"tools":[
               {"name":"self.get_status",
                "description":"读取设备状态，调用 self.get_status 获取",
                "inputSchema":{"type":"object","properties":{}}},
               {"name":"self.audio_speaker",
                "description":"读取扬声器状态，调用 self.audio_speaker 获取",
                "inputSchema":{"type":"object","properties":{}}},
               {"name":"self.audio_speaker.set_volume",
                "description":"调整音量，先查 self.audio_speaker 再调 self.audio_speaker.set_volume",
                "inputSchema":{"type":"object","properties":{"volume":{"type":"integer"}}}}
             ],
             "nextCursor":""}""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolTestHarness harness;
    private DeviceMcpService deviceMcpService;
    private DeviceAuthService deviceAuthService;

    @BeforeEach
    void setUp() {
        harness = ProtocolTestHarness.create();

        deviceAuthService = new DeviceAuthService();
        ReflectionTestUtils.setField(deviceAuthService, "secret", AUTH_SECRET);
        ReflectionTestUtils.setField(deviceAuthService, "expireSeconds", 3600L);
        ReflectionTestUtils.setField(deviceAuthService, "allowedDevicesConfig", "");
        ReflectionTestUtils.invokeMethod(deviceAuthService, "init");

        ServerAddressProvider addressProvider = mock(ServerAddressProvider.class);
        when(addressProvider.getServerAddress()).thenReturn(SERVER_ADDRESS);

        deviceMcpService = new DeviceMcpService();
        ReflectionTestUtils.setField(deviceMcpService, "environment", mock(Environment.class));
        ReflectionTestUtils.setField(deviceMcpService, "serverAddressProvider", addressProvider);
        ReflectionTestUtils.setField(deviceMcpService, "deviceAuthService", deviceAuthService);
        ReflectionTestUtils.setField(deviceMcpService, "deviceRepository", harness.deviceRepository());
        ReflectionTestUtils.setField(deviceMcpService, "sessionManager", harness.sessionManager());
        ReflectionTestUtils.setField(deviceMcpService, "maxToolsCount", 32);
        // 换掉脚手架注入的 mock，让 hello 触发的是真实握手
        ReflectionTestUtils.setField(harness.webSocketHandler(), "deviceMcpService", deviceMcpService);
    }

    @AfterEach
    void tearDown() {
        harness.shutdown();
    }

    @Test
    void helloWithMcpFeatureRunsInitializeAndToolsListHandshake() {
        FakeDevice device = harness.connect(DEVICE_ID);

        // 设备在 hello 里声明 features.mcp=true，服务端异步发起 MCP 握手
        device.hello(1, true, false);
        ChatSession session = device.session();

        JsonNode initialize = awaitMcpRequest(device, "initialize");
        JsonNode vision = initialize.path("payload").path("params").path("capabilities").path("vision");
        assertThat(vision.path("url").asText()).isEqualTo(SERVER_ADDRESS + "/api/vl/chat");
        // 下发的视觉 token 必须能被服务端自己的校验器验回来，且绑定本会话与本设备
        DeviceAuthService.VisionToken visionToken = deviceAuthService.verifyVisionToken(vision.path("token").asText());
        assertThat(visionToken).isNotNull();
        assertThat(visionToken.sessionId()).isEqualTo(device.sessionId());
        assertThat(visionToken.deviceId()).isEqualTo(DEVICE_ID);
        replyAfterRegistered(device, session, requestIdOf(initialize), INITIALIZE_RESULT);

        JsonNode toolsList = awaitMcpRequest(device, "tools/list");
        // 首次拉工具列表带空游标
        assertThat(toolsList.path("payload").path("params").path("cursor").asText()).isEmpty();
        replyAfterRegistered(device, session, requestIdOf(toolsList), TOOLS_LIST_RESULT);

        // mcp_list 写回发生在工具注册之后，等到它就说明整轮握手已经跑完
        AwaitHelper.until("MCP 握手跑完，工具已注册且 mcp_list 已写回",
                () -> session.getDevice().getMcpList() != null);

        // 握手只发这两条请求，且顺序固定
        assertThat(mcpMethods(device)).containsExactly("initialize", "tools/list");
        assertThat(session.getDeviceMcpHolder().isMcpInitialized()).isTrue();
        // nextCursor 为空表示分页结束，游标要被清掉
        assertThat(session.getDeviceMcpHolder().getMcpCursor()).isNull();

        // 注册名是 sanitize 后的，'.' 全部换成 '_'
        assertThat(session.getToolsSessionHolder().getAllFunctionName())
                .containsExactlyInAnyOrder("self_get_status", "self_audio_speaker", "self_audio_speaker_set_volume");
        assertThat(session.getToolsSessionHolder().getFunction("self.get_status")).isNull();

        // description 里引用的原名同步替换，长名先替换，短名不能把长名截成 self_audio_speaker.set_volume
        String description = session.getToolsSessionHolder()
                .getFunction("self_audio_speaker_set_volume").getToolDefinition().description();
        assertThat(description).contains("self_audio_speaker", "self_audio_speaker_set_volume");
        assertThat(description).doesNotContain("self.");

        // 落库的 mcp_list 用的是设备上报的原名，不是 sanitize 后的名字
        assertThat(session.getDevice().getMcpList())
                .isEqualTo("self.get_status,self.audio_speaker,self.audio_speaker.set_volume");
    }

    /**
     * MCP 请求必须先登记 pending 表再出站。反过来的话设备秒回时应答落进空表被直接丢弃，
     * 调用方干等 30 秒超时。
     */
    @Test
    void pendingRequestIsRegisteredBeforeMessageIsSent() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();

        AtomicLong sentRequestId = new AtomicLong(-1);
        AtomicReference<Boolean> registeredAtSendTime = new AtomicReference<>();
        // 在"消息刚发出那一刻"回查 pending 表：设备秒回时应答就是在这个时刻到达的
        device.transport().onTextSent(text -> {
            JsonNode node = parse(text);
            if (!"mcp".equals(node.path("type").asText())) {
                return;
            }
            long id = node.path("payload").path("id").asLong();
            Map<Long, CompletableFuture<DeviceMcpMessage>> pending =
                    session.getDeviceMcpHolder().getMcpPendingRequests();
            registeredAtSendTime.set(pending.containsKey(id));
            sentRequestId.set(id);
        });

        DeviceMcpMessage request = toolCall(session, "self.get_status");
        AtomicReference<DeviceMcpMessage> response = new AtomicReference<>();
        Thread.startVirtualThread(() -> response.set(deviceMcpService.sendMcpRequest(session, request)));

        AwaitHelper.until("MCP 请求已出站", () -> sentRequestId.get() > 0);
        // 回包等 pending 登记完成，用例不真的去等 30 秒超时
        replyAfterRegistered(device, session, sentRequestId.get(), "{\"isError\":\"false\",\"content\":\"ok\"}");
        AwaitHelper.until("在途请求已收到应答", () -> response.get() != null);
        assertThat(response.get().getPayload().getResult()).containsEntry("content", "ok");

        assertThat(registeredAtSendTime.get()).isTrue();
        // 请求结束后表里不能留下条目
        assertThat(session.getDeviceMcpHolder().getMcpPendingRequests()).isEmpty();
    }

    // 设备不应答时要让模型知道是超时而不是笼统的失败，否则模型无从判断该重试还是告诉用户
    @Test
    void timedOutCallTellsModelDeviceDidNotRespond() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        ReflectionTestUtils.setField(deviceMcpService, "mcpRequestTimeoutSeconds", 0);

        DeviceMcpService.McpCallResult result =
                deviceMcpService.call(session, toolCall(session, "self.get_status"));

        assertThat(result.failed()).isTrue();
        assertThat(result.failureReason()).contains("没有响应");
        // 超时后不能在表里留下条目
        assertThat(session.getDeviceMcpHolder().getMcpPendingRequests()).isEmpty();
    }

    // 连接已断时的失败原因要与超时区分开
    @Test
    void sendFailureTellsModelDeviceIsUnreachable() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        device.transport().breakConnection();

        DeviceMcpService.McpCallResult result =
                deviceMcpService.call(session, toolCall(session, "self.get_status"));

        assertThat(result.failed()).isTrue();
        assertThat(result.failureReason()).contains("连接不可用");
        assertThat(session.getDeviceMcpHolder().getMcpPendingRequests()).isEmpty();
    }

    @Test
    void mcpResponseWithUnknownIdIsIgnored() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();

        DeviceMcpMessage request = toolCall(session, "self.get_status");
        AtomicReference<DeviceMcpMessage> response = new AtomicReference<>();
        Thread.startVirtualThread(() -> response.set(deviceMcpService.sendMcpRequest(session, request)));

        JsonNode sent = awaitMcpRequest(device, "tools/call");
        long id = requestIdOf(sent);
        Map<Long, CompletableFuture<DeviceMcpMessage>> pending =
                session.getDeviceMcpHolder().getMcpPendingRequests();
        AwaitHelper.until("在途请求已登记进 pending 表", () -> pending.containsKey(id));

        int outboundBefore = device.transport().outbound().size();
        // 设备回了一条 id 不在 pending 表里的应答，分发是同步的，返回即代表已处理完
        device.mcpReply(id + 12345L, "{\"isError\":\"false\",\"content\":\"stale\"}");

        assertThat(pending).containsOnlyKeys(Long.valueOf(id));
        assertThat(pending.get(id).isDone()).isFalse();
        assertThat(device.transport().isOpen()).isTrue();
        // 未知 id 不该触发任何下行消息
        assertThat(device.transport().outbound()).hasSize(outboundBefore);

        // 合法 id 的应答仍能正确 complete 对应的 future
        device.mcpReply(id, "{\"isError\":\"false\",\"content\":\"ok\"}");
        AwaitHelper.until("在途请求已收到应答", () -> response.get() != null);
        assertThat(response.get().getPayload().getId()).isEqualTo(id);
        assertThat(response.get().getPayload().getResult()).containsEntry("content", "ok");
        assertThat(pending).isEmpty();
    }

    // ========== 驱动与读取辅助 ==========

    /** 等待某个 method 的 MCP 请求出站并返回整条报文 */
    private static JsonNode awaitMcpRequest(FakeDevice device, String method) {
        return AwaitHelper.untilPresent("出站 MCP 请求 " + method,
                () -> mcpRequest(device, method).orElse(null));
    }

    private static Optional<JsonNode> mcpRequest(FakeDevice device, String method) {
        return device.transport().jsonMessages().stream()
                .filter(node -> "mcp".equals(node.path("type").asText()))
                .filter(node -> method.equals(node.path("payload").path("method").asText()))
                .findFirst();
    }

    /** 出站 MCP 请求的 method 序列，用于断言握手顺序 */
    private static List<String> mcpMethods(FakeDevice device) {
        return device.transport().jsonMessages().stream()
                .filter(node -> "mcp".equals(node.path("type").asText()))
                .map(node -> node.path("payload").path("method").asText())
                .filter(method -> !method.isEmpty())
                .toList();
    }

    private static long requestIdOf(JsonNode mcpMessage) {
        return mcpMessage.path("payload").path("id").asLong();
    }

    /**
     * 等 pending 表登记完成再回包。
     * 生产代码是先发消息后登记，用例若在发送那一刻立即回包，应答会被丢弃并阻塞 30 秒。
     */
    private static void replyAfterRegistered(FakeDevice device, ChatSession session, long id, String resultJson) {
        AwaitHelper.until("MCP 请求 " + id + " 已登记进 pending 表",
                () -> session.getDeviceMcpHolder().getMcpPendingRequests().containsKey(id));
        device.mcpReply(id, resultJson);
    }

    private static DeviceMcpMessage toolCall(ChatSession session, String toolName) {
        DeviceMcpMessage message = new DeviceMcpMessage();
        message.setSessionId(session.getSessionId());
        DeviceMcpPayload payload = new DeviceMcpPayload();
        payload.setMethod("tools/call");
        payload.setId(session.getDeviceMcpHolder().getMcpRequestId());
        payload.setParams(Map.of("name", toolName, "arguments", Map.of()));
        message.setPayload(payload);
        return message;
    }

    private static JsonNode parse(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("出站消息不是合法 JSON: " + text, e);
        }
    }
}
