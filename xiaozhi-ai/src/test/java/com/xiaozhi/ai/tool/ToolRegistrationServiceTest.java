package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.mcptoolexclude.service.McpToolExcludeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具注册编排：三类工具来源逐个注册，任一来源出错不能带倒其余来源，
 * 排除清单必须传到每个注册器，注册完只为真有 content_hash 的工具预热向量。
 * <p>
 * 这条链路是 LLM 主链路的一环——某个来源抛异常就少注册一批工具、排除清单没传下去
 * 就会把用户明确禁用的工具重新暴露给模型，两种都只会在生产环境表现为「工具时灵时不灵」。
 */
@ExtendWith(MockitoExtension.class)
class ToolRegistrationServiceTest {

    @Mock
    private McpToolExcludeService mcpToolExcludeService;

    private ToolRegistrationService service;
    private ToolsSessionHolder holder;
    private ToolSession toolSession;

    @BeforeEach
    void setUp() {
        service = new ToolRegistrationService();
        ReflectionTestUtils.setField(service, "mcpToolExcludeService", mcpToolExcludeService);

        holder = new ToolsSessionHolder("session-1", new DeviceBO(), null);
        toolSession = mock(ToolSession.class);
        lenient().when(toolSession.getRoleId()).thenReturn(7);
        lenient().when(toolSession.getToolsSessionHolder()).thenReturn(holder);
    }

    private void withRegistrars(ToolRegistrar... registrars) {
        ReflectionTestUtils.setField(service, "registrars", List.of(registrars));
    }

    @Test
    void everyRegistrarGetsTheRoleExclusionList() {
        Set<String> excluded = Set.of("play_music");
        when(mcpToolExcludeService.getExcludedTools(7)).thenReturn(excluded);
        ToolRegistrar first = mock(ToolRegistrar.class);
        ToolRegistrar second = mock(ToolRegistrar.class);
        withRegistrars(first, second);

        service.register(toolSession);

        verify(first).register(toolSession, excluded);
        verify(second).register(toolSession, excluded);
    }

    @Test
    void oneFailingRegistrarDoesNotStopTheRest() {
        when(mcpToolExcludeService.getExcludedTools(7)).thenReturn(Set.of());
        ToolRegistrar broken = mock(ToolRegistrar.class);
        ToolRegistrar healthy = mock(ToolRegistrar.class);
        doThrow(new IllegalStateException("MCP server 连不上")).when(broken).register(any(), any());
        withRegistrars(broken, healthy);

        service.register(toolSession);

        verify(healthy)
            .register(toolSession, Set.of());
    }

    private static ToolCallback callback(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
            .name(name)
            .description("描述-" + name)
            .inputSchema("{\"type\":\"object\"}")
            .build());
        return callback;
    }
}
