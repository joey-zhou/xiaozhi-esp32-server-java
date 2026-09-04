package com.xiaozhi.memory;

import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.summary.convert.SummaryConvert;
import com.xiaozhi.summary.service.SummaryService;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 转换器用 MapStruct 生成的真实实现，只 mock Service。 */
@ExtendWith(MockitoExtension.class)
class MemoryControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private SummaryService summaryService;

    private MemoryController memoryController;

    @BeforeEach
    void setUp() {
        memoryController = new MemoryController();
        ReflectionTestUtils.setField(memoryController, "summaryService", summaryService);
        ReflectionTestUtils.setField(memoryController, "summaryConvert", Mappers.getMapper(SummaryConvert.class));
        mockMvc = buildMockMvc(memoryController);
    }

    @Test
    void querySummaryReturnsPagedSummaryMemory() throws Exception {
        when(summaryService.page("dev-1", 2, 1, 10)).thenReturn(new PageResult<>(List.of(summary()), 1L, 1, 10));

        mockMvc.perform(get("/api/memory/summary/2/dev-1")
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.list[0].id").value(1))
            .andExpect(jsonPath("$.data.list[0].deviceId").value("dev-1"))
            .andExpect(jsonPath("$.data.list[0].summary").value("用户最近在学手冲咖啡"))
            .andExpect(jsonPath("$.data.list[0].promptTokens").value(12));
    }

    private static SummaryBO summary() {
        return new SummaryBO()
            .setDeviceId("dev-1")
            .setRoleId(2)
            .setSummary("用户最近在学手冲咖啡")
            .setPromptTokens(12)
            .setCompletionTokens(5)
            .setLastMessageTimestamp(Instant.ofEpochMilli(1L))
            .setCreateTime(Instant.ofEpochMilli(1L));
    }
}
