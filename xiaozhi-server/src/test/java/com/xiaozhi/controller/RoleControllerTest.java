package com.xiaozhi.controller;

import com.xiaozhi.role.RoleController;
import com.xiaozhi.role.SherpaVoiceService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.req.RoleCreateReq;
import com.xiaozhi.common.model.req.RolePageReq;
import com.xiaozhi.common.model.req.RoleUpdateReq;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.role.RoleAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

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
class RoleControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private RoleAppService roleAppService;

    @Mock
    private SherpaVoiceService sherpaVoiceService;

    private RoleController roleController;

    @BeforeEach
    void setUp() {
        roleController = new RoleController();
        ReflectionTestUtils.setField(roleController, "roleAppService", roleAppService);
        ReflectionTestUtils.setField(roleController, "sherpaVoiceService", sherpaVoiceService);
        mockMvc = buildMockMvc(roleController);
    }

    @Test
    void listReturnsPagedRolesForCurrentUser() throws Exception {
        RoleResp roleResp = new RoleResp();
        roleResp.setRoleId(1);
        roleResp.setRoleName("管理员");
        PageResp<RoleResp> pageResp = new PageResp<>(List.of(roleResp), 1L, 1, 10);

        when(roleAppService.page(any(RolePageReq.class), eq(7))).thenReturn(pageResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/role")
                    .param("pageNo", "1")
                    .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].roleId").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
        }

        ArgumentCaptor<RolePageReq> captor = ArgumentCaptor.forClass(RolePageReq.class);
        verify(roleAppService).page(captor.capture(), eq(7));
        assertThat(captor.getValue().getPageNo()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void createUsesCurrentUserAndReturnsCreatedRole() throws Exception {
        RoleCreateReq req = new RoleCreateReq();
        req.setRoleName("新角色");

        RoleResp roleResp = new RoleResp();
        roleResp.setRoleId(9);

        when(roleAppService.create(any(RoleCreateReq.class), eq(7))).thenReturn(roleResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.roleId").value(9));
        }

        verify(roleAppService).create(any(RoleCreateReq.class), eq(7));
    }

    @Test
    void createReturnsBadRequestWhenRoleNameBlank() throws Exception {
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"roleName":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value("角色名称不能为空"));
        }
    }

    @Test
    void updateReturnsConflictWhenServiceThrowsIllegalState() throws Exception {
        RoleUpdateReq req = new RoleUpdateReq();
        req.setRoleName("更新后角色");

        when(roleAppService.update(eq(9), any(RoleUpdateReq.class)))
            .thenThrow(new IllegalStateException("更新角色失败"));

        mockMvc.perform(put("/api/role/9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ResultStatus.CONFLICT))
            .andExpect(jsonPath("$.message").value("更新角色失败"));
    }

    @Test
    void deleteReturnsNotFoundWhenRoleMissing() throws Exception {
        doThrow(new ResourceNotFoundException("角色不存在或无权访问")).when(roleAppService).delete(9);

        mockMvc.perform(delete("/api/role/9"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ResultStatus.NOT_FOUND))
            .andExpect(jsonPath("$.message").value("角色不存在或无权访问"));
    }

    @Test
    void sherpaVoicesDelegatesToService() throws Exception {
        when(sherpaVoiceService.listVoices()).thenReturn(List.of(
            Map.of("label", "Alice", "value", "kokoro-demo:kokoro:0"),
            Map.of("label", "Bob", "value", "kokoro-demo:kokoro:1")
        ));

        mockMvc.perform(get("/api/role/sherpaVoices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data[0].label").value("Alice"))
            .andExpect(jsonPath("$.data[0].value").value("kokoro-demo:kokoro:0"))
            .andExpect(jsonPath("$.data[1].label").value("Bob"))
            .andExpect(jsonPath("$.data[1].value").value("kokoro-demo:kokoro:1"));
    }
}
