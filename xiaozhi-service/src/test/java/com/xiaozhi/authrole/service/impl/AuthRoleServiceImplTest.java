package com.xiaozhi.authrole.service.impl;

import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.authrole.dal.mysql.mapper.AuthRoleMapper;
import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PermissionResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRoleServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(AuthRoleDO.class, AuthRolePermissionDO.class);
    }

    @Mock
    private AuthRoleMapper authRoleMapper;

    @Mock
    private com.xiaozhi.user.dal.mysql.mapper.UserMapper userMapper;

    @Mock
    private AuthRolePermissionMapper authRolePermissionMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthRoleConvert authRoleConvert;

    @InjectMocks
    private AuthRoleServiceImpl authRoleService;

    @Test
    void getThrowsWhenAuthRoleIdMissing() {
        assertThatThrownBy(() -> authRoleService.get(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("权限角色ID不能为空");
    }

    @Test
    void getPermissionConfigAssemblesRoleAndPermissionInfo() {
        AuthRoleDO authRoleDO = new AuthRoleDO();
        authRoleDO.setAuthRoleId(1);
        AuthRoleResp authRoleResp = new AuthRoleResp();
        authRoleResp.setAuthRoleId(1);
        authRoleResp.setAuthRoleName("管理员");
        authRoleResp.setRoleKey("admin");
        PermissionTreeResp tree = new PermissionTreeResp();
        tree.setPermissionId(10);

        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO);
        when(authRoleConvert.toResp(authRoleDO)).thenReturn(authRoleResp);
        when(permissionService.listTree()).thenReturn(List.of(tree));
        when(permissionService.listIdsByAuthRoleId(1)).thenReturn(List.of(10, 20));

        AuthRolePermissionConfigResp result = authRoleService.getPermissionConfig(1);

        assertThat(result.getAuthRoleId()).isEqualTo(1);
        assertThat(result.getPermissionTree()).containsExactly(tree);
        assertThat(result.getCheckedPermissionIds()).containsExactly(10, 20);
    }

    @Test
    void assignPermissionsClearsCacheWhenPermissionListEmpty() {
        AuthRoleDO authRoleDO = new AuthRoleDO();
        authRoleDO.setAuthRoleId(1);
        AuthRoleResp authRoleResp = new AuthRoleResp();
        authRoleResp.setAuthRoleId(1);

        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO);
        when(authRoleConvert.toResp(authRoleDO)).thenReturn(authRoleResp);

        authRoleService.assignPermissions(1, List.of());

        verify(authRolePermissionMapper).delete(any());
        verify(permissionService).clearAuthRoleCache(1);
    }

    @Test
    void assignPermissionsPersistsValidPermissionIds() {
        AuthRoleDO authRoleDO = new AuthRoleDO();
        authRoleDO.setAuthRoleId(1);
        AuthRoleResp authRoleResp = new AuthRoleResp();
        authRoleResp.setAuthRoleId(1);

        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO);
        when(authRoleConvert.toResp(authRoleDO)).thenReturn(authRoleResp);

        authRoleService.assignPermissions(1, Arrays.asList(10, null, 20));

        ArgumentCaptor<List<AuthRolePermissionDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(authRolePermissionMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).extracting(AuthRolePermissionDO::getPermissionId)
            .containsExactly(10, 20);
        verify(permissionService).clearAuthRoleCache(1);
    }

    @Test
    void listPermissionsDelegatesToPermissionService() {
        PermissionResp permission = new PermissionResp();
        permission.setPermissionId(1);
        when(permissionService.listByAuthRoleId(1)).thenReturn(List.of(permission));

        List<PermissionResp> result = authRoleService.listPermissions(1);

        assertThat(result).containsExactly(permission);
    }
}
