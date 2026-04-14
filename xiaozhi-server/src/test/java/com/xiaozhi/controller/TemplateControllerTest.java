package com.xiaozhi.controller;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.template.TemplateController;
import com.xiaozhi.common.model.req.TemplateCreateReq;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.TemplateResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.common.model.req.TemplatePageReq;
import com.xiaozhi.template.TemplateAppService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TemplateControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private TemplateAppService templateAppService;

    private TemplateController templateController;

    @BeforeEach
    void setUp() {
        templateController = new TemplateController();
        ReflectionTestUtils.setField(templateController, "templateAppService", templateAppService);
        mockMvc = buildMockMvc(templateController);
    }

    @Test
    void listReturnsPagedTemplatesForCurrentUser() throws Exception {
        TemplateResp resp = new TemplateResp();
        resp.setTemplateId(1);
        resp.setTemplateName("欢迎词");
        PageResp<TemplateResp> pageResp = new PageResp<>(List.of(resp), 1L, 1, 10);
        when(templateAppService.page(any(TemplatePageReq.class), eq(7))).thenReturn(pageResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/template")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("category", "system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].templateId").value(1));
        }
    }

    @Test
    void createReturnsBadRequestWhenTemplateNameMissing() throws Exception {
        TemplateCreateReq req = new TemplateCreateReq();
        req.setTemplateContent("你好");
        req.setCategory("system");

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/template")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value("模板名称不能为空"));
        }
    }

    @Test
    void deleteReturnsNotFoundWhenTemplateMissing() throws Exception {
        doThrow(new ResourceNotFoundException("模板不存在或无权访问")).when(templateAppService).delete(7);

        mockMvc.perform(delete("/api/template/7"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ResultStatus.NOT_FOUND))
            .andExpect(jsonPath("$.message").value("模板不存在或无权访问"));
    }
}
