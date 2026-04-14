package com.xiaozhi.mcptoolexclude.service.impl;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.mcptoolexclude.dal.mysql.dataobject.McpToolExcludeDO;
import com.xiaozhi.mcptoolexclude.dal.mysql.mapper.McpToolExcludeMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolExcludeServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(McpToolExcludeDO.class);
    }

    @Mock
    private McpToolExcludeMapper mcpToolExcludeMapper;

    @InjectMocks
    private McpToolExcludeServiceImpl mcpToolExcludeService;

    @Test
    void getExcludedToolsMergesGlobalAndRoleDisabledTools() {
        McpToolExcludeDO global = new McpToolExcludeDO();
        global.setExcludeTools("[\"tool-a\"]");
        McpToolExcludeDO role = new McpToolExcludeDO();
        role.setExcludeTools("[\"tool-b\"]");

        when(mcpToolExcludeMapper.selectList(any())).thenReturn(List.of(global), List.of(role));

        Set<String> result = mcpToolExcludeService.getExcludedTools(1);

        assertThat(result).containsExactly("tool-a", "tool-b");
    }

    @Test
    void toggleGlobalToolStatusCreatesConfigWhenDisablingNewTool() {
        when(mcpToolExcludeMapper.selectOne(any())).thenReturn(null);
        when(mcpToolExcludeMapper.insert(any(McpToolExcludeDO.class))).thenReturn(1);

        mcpToolExcludeService.toggleGlobalToolStatus("tool-a", null, false);

        ArgumentCaptor<McpToolExcludeDO> captor = ArgumentCaptor.forClass(McpToolExcludeDO.class);
        verify(mcpToolExcludeMapper).insert(captor.capture());
        assertThat(captor.getValue().getExcludeTools()).contains("tool-a");
        assertThat(captor.getValue().getBindKey()).isEqualTo("0");
    }

    @Test
    void toggleRoleToolStatusDeletesConfigWhenNoToolLeft() {
        McpToolExcludeDO config = new McpToolExcludeDO();
        config.setId(1L);
        config.setExcludeTools("[\"tool-a\"]");
        when(mcpToolExcludeMapper.selectOne(any())).thenReturn(config);

        mcpToolExcludeService.toggleRoleToolStatus(2, "tool-a", "server-a", true);

        verify(mcpToolExcludeMapper).deleteById(1L);
    }

    @Test
    void batchSetRoleExcludeToolsReturnsWhenRoleIdMissing() {
        mcpToolExcludeService.batchSetRoleExcludeTools(null, List.of("tool-a"), null);

        verifyNoInteractions(mcpToolExcludeMapper);
    }

    @Test
    void toggleGlobalToolStatusWrapsPersistenceFailure() {
        when(mcpToolExcludeMapper.selectOne(any())).thenReturn(null);
        when(mcpToolExcludeMapper.insert(any(McpToolExcludeDO.class))).thenThrow(new RuntimeException("db error"));

        assertThatThrownBy(() -> mcpToolExcludeService.toggleGlobalToolStatus("tool-a", null, false))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("保存MCP工具排除配置失败");
    }
}
