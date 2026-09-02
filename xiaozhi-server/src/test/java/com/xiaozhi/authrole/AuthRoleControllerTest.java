package com.xiaozhi.authrole;

import com.xiaozhi.common.model.req.AuthRolePageReq;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住权限角色接口：分页查询条件的绑定，以及分配权限时允许空请求体（清空该角色的全部权限）。
 */
@ExtendWith(MockitoExtension.class)
class AuthRoleControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private AuthRoleAppService authRoleAppService;

    private AuthRoleController authRoleController;

    @BeforeEach
    void setUp() {
        authRoleController = new AuthRoleController();
        ReflectionTestUtils.setField(authRoleController, "authRoleAppService", authRoleAppService);
        mockMvc = buildMockMvc(authRoleController);
    }

    @Test
    void listReturnsPagedAuthRoles() throws Exception {
        AuthRoleResp resp = new AuthRoleResp();
        resp.setAuthRoleId(1);
        resp.setAuthRoleName("管理员");
        PageResp<AuthRoleResp> pageResp = new PageResp<>(List.of(resp), 1L, 1, 10);
        when(authRoleAppService.page(any(AuthRolePageReq.class))).thenReturn(pageResp);

        mockMvc.perform(get("/api/auth-role")
                .param("pageNo", "1")
                .param("pageSize", "10")
                .param("roleKey", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].authRoleId").value(1));

        ArgumentCaptor<AuthRolePageReq> captor = ArgumentCaptor.forClass(AuthRolePageReq.class);
        verify(authRoleAppService).page(captor.capture());
        assertThat(captor.getValue().getRoleKey()).isEqualTo("admin");
    }

    @Test
    void assignPermissionsAllowsNullBody() throws Exception {
        AuthRolePermissionConfigResp resp = new AuthRolePermissionConfigResp();
        resp.setCheckedPermissionIds(List.of(1, 2));
        when(authRoleAppService.assignPermissions(eq(9), eq(null))).thenReturn(resp);

        mockMvc.perform(put("/api/auth-role/9/permissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.checkedPermissionIds[0]").value(1));
    }
}
