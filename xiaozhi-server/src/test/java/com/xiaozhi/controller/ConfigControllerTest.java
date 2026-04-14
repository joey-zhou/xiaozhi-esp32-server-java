package com.xiaozhi.controller;

import com.xiaozhi.config.ConfigController;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.config.ConfigAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private ConfigAppService configAppService;

    private ConfigController configController;

    @BeforeEach
    void setUp() {
        configController = new ConfigController();
        ReflectionTestUtils.setField(configController, "configAppService", configAppService);
        mockMvc = buildMockMvc(configController);
    }

    @Test
    void listReturnsPagedConfigsForCurrentUser() throws Exception {
        ConfigResp resp = new ConfigResp();
        resp.setConfigId(3);
        resp.setConfigName("默认TTS");
        PageResp<ConfigResp> pageResp = new PageResp<>(List.of(resp), 1L, 1, 10);
        when(configAppService.page(any(ConfigPageReq.class), eq(7))).thenReturn(pageResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/config")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("configType", "tts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].configId").value(3));
        }

        ArgumentCaptor<ConfigPageReq> captor = ArgumentCaptor.forClass(ConfigPageReq.class);
        verify(configAppService).page(captor.capture(), eq(7));
        assertThat(captor.getValue().getConfigType()).isEqualTo("tts");
    }

    @Test
    void updateReturnsUpdatedConfig() throws Exception {
        ConfigResp updated = new ConfigResp();
        updated.setConfigId(11);
        when(configAppService.update(eq(11), any(ConfigUpdateReq.class))).thenReturn(updated);

        mockMvc.perform(put("/api/config/11")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"configName":"新配置"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.configId").value(11));
    }

    @Test
    void createReturnsBadRequestWhenConfigNameMissing() throws Exception {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigType("tts");
        req.setProvider("edge");

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value("配置名称不能为空"));
        }
    }

    @Test
    void deleteReturnsNotFoundWhenConfigMissing() throws Exception {
        doThrow(new ResourceNotFoundException("配置不存在或无权访问"))
            .when(configAppService).delete(9);

        mockMvc.perform(delete("/api/config/9"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ResultStatus.NOT_FOUND))
            .andExpect(jsonPath("$.message").value("配置不存在或无权访问"));
    }
}
