package com.xiaozhi.mcptoolexclude.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.mcptoolexclude.dal.mysql.dataobject.McpToolExcludeDO;
import com.xiaozhi.mcptoolexclude.dal.mysql.mapper.McpToolExcludeMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住 MCP 工具排除配置的查询条件与开关语义：全局与角色两类排除必须按
 * excludeType/bindKey 分别取数，合并后全局项在前。
 */
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

        // 按查询条件而非调用顺序打桩：两条查询的 excludeType/bindKey 若写反，本用例会失败
        when(mcpToolExcludeMapper.selectList(argThat(boundTo("global", "0")))).thenReturn(List.of(global));
        when(mcpToolExcludeMapper.selectList(argThat(boundTo("role", "1")))).thenReturn(List.of(role));

        Set<String> result = mcpToolExcludeService.getExcludedTools(1);

        assertThat(result).containsExactly("tool-a", "tool-b");
    }

    @Test
    void getExcludedToolsSkipsRoleQueryWhenRoleIdMissing() {
        McpToolExcludeDO global = new McpToolExcludeDO();
        global.setExcludeTools("[\"tool-a\"]");

        when(mcpToolExcludeMapper.selectList(argThat(boundTo("global", "0")))).thenReturn(List.of(global));

        assertThat(mcpToolExcludeService.getExcludedTools(null)).containsExactly("tool-a");

        // roleId 为空时不得再发角色维度的查询
        verify(mcpToolExcludeMapper).selectList(any());
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

    /** 匹配按 excludeType + bindKey 两个等值条件构造的查询条件。 */
    private static ArgumentMatcher<Wrapper<McpToolExcludeDO>> boundTo(String excludeType, String bindKey) {
        return wrapper -> {
            if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
                return false;
            }
            // MyBatis-Plus 的条件片段是惰性求值的，不先取一次 SQL，paramNameValuePairs 是空的
            abstractWrapper.getTargetSql();
            Collection<Object> values = abstractWrapper.getParamNameValuePairs().values();
            return values.contains(excludeType) && values.contains(bindKey);
        };
    }
}
