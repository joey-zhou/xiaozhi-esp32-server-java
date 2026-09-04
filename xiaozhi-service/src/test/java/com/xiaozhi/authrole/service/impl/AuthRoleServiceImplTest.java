package com.xiaozhi.authrole.service.impl;

import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.authrole.dal.mysql.mapper.AuthRoleMapper;
import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.AuthRoleBO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住后台权限角色读侧：不存在时返 null 而不是抛异常；
 * 授权保存要先校验角色存在、过滤空 ID 并清掉权限缓存。
 */
@ExtendWith(MockitoExtension.class)
class AuthRoleServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(AuthRoleDO.class, AuthRolePermissionDO.class);
    }

    @Mock
    private AuthRoleMapper authRoleMapper;

    @Mock
    private AuthRolePermissionMapper authRolePermissionMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthRoleConvert authRoleConvert;

    @InjectMocks
    private AuthRoleServiceImpl authRoleService;

    private static AuthRoleDO authRoleDO(Integer authRoleId, String roleKey) {
        AuthRoleDO authRoleDO = new AuthRoleDO();
        authRoleDO.setAuthRoleId(authRoleId);
        authRoleDO.setRoleKey(roleKey);
        return authRoleDO;
    }

    @Test
    void getBOReturnsNullWhenAuthRoleIdMissing() {
        assertThat(authRoleService.getBO(null)).isNull();
        verifyNoInteractions(authRoleMapper, authRoleConvert);
    }

    @Test
    void getBOReturnsNullWhenAuthRoleNotFound() {
        when(authRoleMapper.selectById(1)).thenReturn(null);

        assertThat(authRoleService.getBO(1)).isNull();
    }

    @Test
    void getBOConvertsStoredAuthRole() {
        AuthRoleDO authRoleDO = authRoleDO(1, "admin");
        AuthRoleBO authRoleBO = new AuthRoleBO();
        authRoleBO.setAuthRoleId(1);
        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO);
        when(authRoleConvert.toBO(authRoleDO)).thenReturn(authRoleBO);

        assertThat(authRoleService.getBO(1)).isSameAs(authRoleBO);
    }

    @Test
    void getRoleKeyReturnsNullWhenAuthRoleNotFound() {
        when(authRoleMapper.selectById(1)).thenReturn(null);

        assertThat(authRoleService.getRoleKey(1)).isNull();
        assertThat(authRoleService.getRoleKey(null)).isNull();
    }

    @Test
    void getRoleKeyReturnsStoredRoleKey() {
        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO(1, "manager"));

        assertThat(authRoleService.getRoleKey(1)).isEqualTo("manager");
        verifyNoInteractions(authRoleConvert);
    }

    @Test
    void assignPermissionsThrowsWhenAuthRoleNotFound() {
        when(authRoleMapper.selectById(1)).thenReturn(null);

        assertThatThrownBy(() -> authRoleService.assignPermissions(1, List.of(10)))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("权限角色不存在");
        verifyNoInteractions(authRolePermissionMapper, permissionService);
    }

    @Test
    void assignPermissionsClearsCacheWhenPermissionListEmpty() {
        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO(1, "admin"));

        authRoleService.assignPermissions(1, List.of());

        verify(authRolePermissionMapper).delete(any());
        verify(permissionService).clearAuthRoleCache(1);
    }

    @Test
    void assignPermissionsPersistsValidPermissionIds() {
        when(authRoleMapper.selectById(1)).thenReturn(authRoleDO(1, "admin"));

        authRoleService.assignPermissions(1, Arrays.asList(10, null, 20));

        ArgumentCaptor<List<AuthRolePermissionDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(authRolePermissionMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).extracting(AuthRolePermissionDO::getPermissionId)
            .containsExactly(10, 20);
        verify(permissionService).clearAuthRoleCache(1);
    }
}
