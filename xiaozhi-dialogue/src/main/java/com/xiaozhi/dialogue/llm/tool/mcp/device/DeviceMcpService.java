package com.xiaozhi.dialogue.llm.tool.mcp.device;

import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.domain.DeviceMcpMessage;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpClientInfo;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpInitialize;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpPayload;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpVision;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DeviceMcpService {
    @Resource
    private Environment environment;

    @Resource
    private ServerAddressProvider serverAddressProvider;

    @Resource
    private DeviceRepository deviceRepository;

    @Resource
    private SessionManager sessionManager;

    @Value("${xiaozhi.mcp.device.max-tools-count:32}")
    private int maxToolsCount = 32;

    /**
     * 初始化设备端MCP工具列表，并将能力列表持久化到数据库
     */
    public void initialize(ChatSession chatSession) {
        DeviceMcpMessage initResult = sendInitialize(chatSession);
        if (initResult != null) {
            chatSession.getDeviceMcpHolder().setMcpInitialized(true);
        }
        if (chatSession.getDeviceMcpHolder().isMcpInitialized()) {
            List<String> toolNames = sendToolsList(chatSession, null);
            persistMcpList(chatSession, toolNames);
        }
    }

    /**
     * 初始化设备端MCP工具列表（包含用户工具），并将能力列表持久化到数据库
     */
    public void initializeWithUserTools(ChatSession chatSession) {
        DeviceMcpMessage initResult = sendInitialize(chatSession);
        if (initResult != null) {
            chatSession.getDeviceMcpHolder().setMcpInitialized(true);
        }
        if (chatSession.getDeviceMcpHolder().isMcpInitialized()) {
            List<String> toolNames = sendToolsList(chatSession, true);
            persistMcpList(chatSession, toolNames);
        }
    }

    /**
     * 服务端主动调用设备 MCP 工具
     *
     * @param deviceId 设备ID（设备必须在线）
     * @param toolName 工具原始名称（如 "screenshot"、"self.reboot"）
     * @param args     工具参数
     * @return MCP 响应的 result 字段
     */
    public Map<String, Object> callDeviceTool(String deviceId, String toolName, Map<String, Object> args) {
        ChatSession chatSession = sessionManager.getSessionByDeviceId(deviceId);
        if (chatSession == null) {
            throw new IllegalStateException("设备离线或未连接: " + deviceId);
        }

        DeviceMcpMessage request = new DeviceMcpMessage();
        request.setSessionId(chatSession.getSessionId());
        DeviceMcpPayload payload = new DeviceMcpPayload();
        payload.setMethod("tools/call");
        payload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
        payload.setParams(Map.of(
                "name", toolName,
                "arguments", args != null ? args : Map.of()
        ));
        request.setPayload(payload);

        DeviceMcpMessage response = sendMcpRequest(chatSession, request);
        if (response == null) {
            throw new IllegalStateException("设备响应超时: " + toolName);
        }
        if (response.getPayload().getResult() == null) {
            throw new IllegalStateException("工具调用失败: " + response.getPayload().getError());
        }
        return response.getPayload().getResult();
    }

    /**
     * 发送初始化命令
     */
    protected DeviceMcpMessage sendInitialize(ChatSession chatSession) {
        DeviceMcpMessage message = new DeviceMcpMessage();
        message.setSessionId(chatSession.getSessionId());
        DeviceMcpPayload payload = new DeviceMcpPayload();
        payload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
        payload.setMethod("initialize");
        payload.setParams(deviceMcpInitialize(chatSession));
        message.setPayload(payload);

        DeviceMcpMessage result = sendMcpRequest(chatSession, message);
        if (result != null) {
            log.debug("SessionId: {}, MCP initialized successfully", chatSession.getSessionId());
            return result;
        }
        return null;
    }

    @NotNull
    private DeviceMcpInitialize deviceMcpInitialize(ChatSession chatSession) {
        DeviceMcpInitialize initialize = new DeviceMcpInitialize();
        initialize.setClientInfo(new DeviceMcpClientInfo());

        DeviceMcpVision vision = new DeviceMcpVision();
        vision.setUrl(serverAddressProvider.getServerAddress() + "/api/vl/chat");
        vision.setToken(chatSession.getSessionId());
        initialize.setCapabilities(Map.of("vision", vision));
        return initialize;
    }

    /**
     * 发送工具列表请求（支持分页递归）
     *
     * @return 本次及后续分页中收集到的所有原始工具名
     */
    private List<String> sendToolsList(ChatSession chatSession, Boolean withUserTools) {
        DeviceMcpMessage message = new DeviceMcpMessage();
        message.setSessionId(chatSession.getSessionId());
        DeviceMcpPayload payload = new DeviceMcpPayload();
        payload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
        payload.setMethod("tools/list");
        if (withUserTools != null && withUserTools) {
            payload.setParams(Map.of("withUserTools", true));
        } else if (chatSession.getDeviceMcpHolder().getMcpCursor() != null) {
            payload.setParams(Map.of("cursor", chatSession.getDeviceMcpHolder().getMcpCursor()));
        } else {
            payload.setParams(Map.of("cursor", ""));
        }
        message.setPayload(payload);

        List<String> collectedNames = new ArrayList<>();
        DeviceMcpMessage result = sendMcpRequest(chatSession, message);
        if (result == null) {
            return collectedNames;
        }

        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.getPayload().getResult().get("tools");
        Object nextCursor = result.getPayload().getResult().get("nextCursor");
        int toolsCount = chatSession.getToolCallbacks().size();

        if (tools.isEmpty() || (toolsCount + tools.size()) > maxToolsCount) {
            return collectedNames;
        }

        for (Map<String, Object> tool : tools) {
            String name = (String) tool.get("name");
            String funcName = "mcp_" + name.replace(".", "_");
            String funcDescription = (String) tool.get("description");
            Object inputSchema = tool.get("inputSchema");

            ToolCallback toolCallback = FunctionToolCallback
                    .builder(funcName, (Map<String, Object> params, ToolContext toolContext) -> {
                        DeviceMcpMessage req = new DeviceMcpMessage();
                        req.setSessionId(chatSession.getSessionId());
                        DeviceMcpPayload reqPayload = new DeviceMcpPayload();
                        reqPayload.setMethod("tools/call");
                        reqPayload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
                        reqPayload.setParams(Map.of("name", name, "arguments", params));
                        req.setPayload(reqPayload);

                        DeviceMcpMessage resp = sendMcpRequest(chatSession, req);
                        if (resp == null) {
                            return "操作失败";
                        }
                        log.info("SessionId: {}, MCP function call response: {}", chatSession.getSessionId(), resp);
                        if (resp.getPayload().getResult() == null) {
                            return resp.getPayload().getError().get("message");
                        }
                        if ("false".equals(String.valueOf(resp.getPayload().getResult().get("isError")))) {
                            return resp.getPayload().getResult().get("content");
                        } else {
                            return resp.getPayload().getError();
                        }
                    })
                    .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                    .description(funcDescription)
                    .inputSchema(JsonUtil.toJson(inputSchema))
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();

            chatSession.getToolsSessionHolder().registerFunction(funcName, toolCallback);
            collectedNames.add(name);
        }

        if (nextCursor != null && !nextCursor.toString().isEmpty()) {
            chatSession.getDeviceMcpHolder().setMcpCursor(nextCursor.toString());
            collectedNames.addAll(sendToolsList(chatSession, null));
        } else {
            chatSession.getDeviceMcpHolder().setMcpCursor(null);
        }
        return collectedNames;
    }

    private void persistMcpList(ChatSession chatSession, List<String> toolNames) {
        if (toolNames.isEmpty()) {
            return;
        }
        String deviceId = chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        String mcpList = String.join(",", toolNames);
        // 与 session 内存中的值比较，相同则跳过，无需查库
        if (Objects.equals(chatSession.getDevice().getMcpList(), mcpList)) {
            return;
        }
        try {
            deviceRepository.findById(deviceId).ifPresent(device -> {
                device.updateMcpList(mcpList);
                deviceRepository.save(device);
            });
            chatSession.getDevice().setMcpList(mcpList);
            log.info("DeviceId: {}, mcp_list updated: {}", deviceId, mcpList);
        } catch (Exception e) {
            log.warn("DeviceId: {}, failed to persist mcp_list", deviceId, e);
        }
    }

    public DeviceMcpMessage sendMcpRequest(ChatSession chatSession, DeviceMcpMessage mcpMessage) {
        Long id = mcpMessage.getPayload().getId();
        CompletableFuture<DeviceMcpMessage> future = new CompletableFuture<>();
        chatSession.sendTextMessage(JsonUtil.toJson(mcpMessage));
        chatSession.getDeviceMcpHolder().getMcpPendingRequests().put(id, future);

        DeviceMcpMessage response = null;
        try {
            response = future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("SessionId: {}, Error sending MCP request：{}", chatSession.getSessionId(), e);
            chatSession.getDeviceMcpHolder().getMcpPendingRequests().remove(id);
        }
        return response;
    }
}
