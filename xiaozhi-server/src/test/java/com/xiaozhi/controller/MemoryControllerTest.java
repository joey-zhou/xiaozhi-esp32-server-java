package com.xiaozhi.controller;

import com.xiaozhi.memory.MemoryController;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.summary.service.SummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        mockMvc = buildMockMvc(memoryController);
    }

    @Test
    void querySummaryReturnsPagedSummaryMemory() throws Exception {
        SummaryBO summaryBO = new SummaryBO();
        summaryBO.setCreateTime(Instant.ofEpochMilli(1L));
        PageResp<SummaryBO> pageResp = new PageResp<>(List.of(summaryBO), 1L, 1, 10);
        when(summaryService.page("dev-1", 2, 1, 10)).thenReturn(pageResp);

        mockMvc.perform(get("/api/memory/summary/2/dev-1")
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.list[0].id").value(1));
    }

}
