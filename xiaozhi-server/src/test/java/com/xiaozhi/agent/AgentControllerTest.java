package com.xiaozhi.agent;

import com.xiaozhi.agent.convert.AgentConvert;
import com.xiaozhi.agent.service.AgentService;
import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住智能体分页接口：查询条件必须从 query 参数绑定后逐项传给 Service，用户维度取自当前登录态。
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private AgentService agentService;

    private AgentController agentController;

    @BeforeEach
    void setUp() {
        agentController = new AgentController();
        ReflectionTestUtils.setField(agentController, "agentService", agentService);
        ReflectionTestUtils.setField(agentController, "agentConvert", Mappers.getMapper(AgentConvert.class));
        mockMvc = buildMockMvc(agentController);
    }

    @Test
    void listReturnsPagedAgentsForCurrentUser() throws Exception {
        AgentBO agent = new AgentBO();
        agent.setAgentId(1);
        agent.setAgentName("讲解员");
        when(agentService.page(1, 10, "coze", null, 7)).thenReturn(new PageResult<>(List.of(agent), 1L, 1, 10));

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

        verify(agentService).page(1, 10, "coze", null, 7);
    }
}
