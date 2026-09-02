package com.xiaozhi.agent;

import com.xiaozhi.common.model.req.AgentPageReq;
import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住智能体分页接口：查询条件必须从 query 参数绑定进 AgentPageReq，用户维度取自当前登录态。
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private AgentAppService agentAppService;

    private AgentController agentController;

    @BeforeEach
    void setUp() {
        agentController = new AgentController();
        ReflectionTestUtils.setField(agentController, "agentAppService", agentAppService);
        mockMvc = buildMockMvc(agentController);
    }

    @Test
    void listReturnsPagedAgentsForCurrentUser() throws Exception {
        AgentResp agentResp = new AgentResp();
        agentResp.setAgentId(1);
        agentResp.setAgentName("讲解员");
        PageResp<AgentResp> pageResp = new PageResp<>(List.of(agentResp), 1L, 1, 10);
        when(agentAppService.page(any(AgentPageReq.class), eq(7))).thenReturn(pageResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/agent")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("provider", "coze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].agentId").value(1))
                .andExpect(jsonPath("$.data.list[0].agentName").value("讲解员"));
        }

        ArgumentCaptor<AgentPageReq> captor = ArgumentCaptor.forClass(AgentPageReq.class);
        verify(agentAppService).page(captor.capture(), eq(7));
        assertThat(captor.getValue().getProvider()).isEqualTo("coze");
    }
}
