package com.xiaozhi.authrole;

import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.AuthRoleBO;
import com.xiaozhi.common.model.bo.PermissionBO;
import com.xiaozhi.common.model.req.AuthRolePageReq;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.permission.convert.PermissionConvert;
import com.xiaozhi.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住授权配置的组装：角色不存在时抛 ResourceNotFoundException，
 * 存在时把权限树与已选 ID 挂到角色字段之上。
 */
@ExtendWith(MockitoExtension.class)
class AuthRoleAppServiceTest {

    @Mock
    private AuthRoleService authRoleService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthRoleConvert authRoleConvert;

    @Mock
    private PermissionConvert permissionConvert;

    private AuthRoleAppService authRoleAppService;

    @BeforeEach
    void setUp() {
        authRoleAppService = new AuthRoleAppService();
        ReflectionTestUtils.setField(authRoleAppService, "authRoleService", authRoleService);
        ReflectionTestUtils.setField(authRoleAppService, "permissionService", permissionService);
        ReflectionTestUtils.setField(authRoleAppService, "authRoleConvert", authRoleConvert);
        ReflectionTestUtils.setField(authRoleAppService, "permissionConvert", permissionConvert);
    }

    private static AuthRoleBO authRole(Integer authRoleId) {
        AuthRoleBO authRole = new AuthRoleBO();
        authRole.setAuthRoleId(authRoleId);
        authRole.setAuthRoleName("管理员");
        return authRole;
    }

    @Test
    void pageMapsEachRowToResp() {
        AuthRoleBO authRole = authRole(1);
        AuthRoleResp resp = new AuthRoleResp();
        resp.setAuthRoleId(1);
        AuthRolePageReq req = new AuthRolePageReq();
        req.setRoleKey("admin");
        when(authRoleService.page(req.getPageNo(), req.getPageSize(), null, "admin", null))
            .thenReturn(new PageResult<>(List.of(authRole), 1L, 1, 10));
        when(authRoleConvert.toResp(authRole)).thenReturn(resp);

        PageResult<AuthRoleResp> result = authRoleAppService.page(req);

        assertThat(result.getList()).containsExactly(resp);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void getPermissionConfigThrowsWhenAuthRoleMissing() {
        when(authRoleService.getBO(1)).thenReturn(null);

        assertThatThrownBy(() -> authRoleAppService.getPermissionConfig(1))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("权限角色不存在");
        verifyNoInteractions(permissionService, authRoleConvert, permissionConvert);
    }

    @Test
    void getPermissionConfigAssemblesRoleAndPermissionInfo() {
        AuthRoleBO authRole = authRole(1);
        AuthRolePermissionConfigResp config = new AuthRolePermissionConfigResp();
        config.setAuthRoleId(1);
        config.setAuthRoleName("管理员");
        PermissionBO permission = new PermissionBO();
        permission.setPermissionId(10);
        PermissionTreeResp tree = new PermissionTreeResp();
        tree.setPermissionId(10);

        when(authRoleService.getBO(1)).thenReturn(authRole);
        when(authRoleConvert.toPermissionConfigResp(authRole)).thenReturn(config);
        when(permissionService.listTree()).thenReturn(List.of(permission));
        when(permissionConvert.toTreeResp(permission)).thenReturn(tree);
        when(permissionService.listIdsByAuthRoleId(1)).thenReturn(List.of(10, 20));

        AuthRolePermissionConfigResp result = authRoleAppService.getPermissionConfig(1);

        assertThat(result.getAuthRoleId()).isEqualTo(1);
        assertThat(result.getAuthRoleName()).isEqualTo("管理员");
        assertThat(result.getPermissionTree()).containsExactly(tree);
        assertThat(result.getCheckedPermissionIds()).containsExactly(10, 20);
    }

    @Test
    void assignPermissionsSavesThenReturnsRefreshedConfig() {
        AuthRoleBO authRole = authRole(1);
        AuthRolePermissionConfigResp config = new AuthRolePermissionConfigResp();
        when(authRoleService.getBO(1)).thenReturn(authRole);
        when(authRoleConvert.toPermissionConfigResp(authRole)).thenReturn(config);
        when(permissionService.listTree()).thenReturn(List.of());
        when(permissionService.listIdsByAuthRoleId(1)).thenReturn(List.of(10));

        AuthRolePermissionConfigResp result = authRoleAppService.assignPermissions(1, List.of(10));

        InOrder inOrder = inOrder(authRoleService, permissionService);
        inOrder.verify(authRoleService).assignPermissions(1, List.of(10));
        inOrder.verify(permissionService).listIdsByAuthRoleId(1);
        assertThat(result.getCheckedPermissionIds()).containsExactly(10);
    }
}
