package com.xiaozhi.security;

import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住 Sa-Token 取角色的规则：超级管理员无论 authRoleId 是否还指向有效角色都拿到 admin。
 */
@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserService userService;

    @Mock
    private AuthRoleService authRoleService;

    private StpInterfaceImpl stpInterface;

    @BeforeEach
    void setUp() {
        stpInterface = new StpInterfaceImpl();
        ReflectionTestUtils.setField(stpInterface, "permissionService", permissionService);
        ReflectionTestUtils.setField(stpInterface, "userService", userService);
        ReflectionTestUtils.setField(stpInterface, "authRoleService", authRoleService);
    }

    private static UserBO user(String isAdmin, Integer authRoleId) {
        UserBO user = new UserBO();
        user.setUserId(1);
        user.setIsAdmin(isAdmin);
        user.setAuthRoleId(authRoleId);
        return user;
    }

    @Test
    void getRoleListKeepsAdminWhenAuthRoleDeleted() {
        when(userService.getBO(1)).thenReturn(user(UserBO.ADMIN_YES, 9));
        when(authRoleService.getRoleKey(9)).thenReturn(null);

        assertThat(stpInterface.getRoleList("1", "login")).containsExactly("admin");
    }

    @Test
    void getRoleListKeepsAdminWhenRoleKeyLookupFails() {
        when(userService.getBO(1)).thenReturn(user(UserBO.ADMIN_YES, 9));
        when(authRoleService.getRoleKey(9)).thenThrow(new IllegalStateException("db down"));

        assertThat(stpInterface.getRoleList("1", "login")).containsExactly("admin");
    }

    @Test
    void getRoleListReturnsAdminAndRoleKeyForAdminUser() {
        when(userService.getBO(1)).thenReturn(user(UserBO.ADMIN_YES, 9));
        when(authRoleService.getRoleKey(9)).thenReturn("manager");

        assertThat(stpInterface.getRoleList(1, "login")).containsExactly("admin", "manager");
    }

    @Test
    void getRoleListReturnsOnlyRoleKeyForNormalUser() {
        when(userService.getBO(1)).thenReturn(user(UserBO.ADMIN_NO, 9));
        when(authRoleService.getRoleKey(9)).thenReturn("manager");

        assertThat(stpInterface.getRoleList("1", "login")).containsExactly("manager");
    }

    @Test
    void getRoleListReturnsEmptyWhenUserMissing() {
        when(userService.getBO(1)).thenReturn(null);

        assertThat(stpInterface.getRoleList("1", "login")).isEmpty();
        verifyNoInteractions(authRoleService);
    }

    @Test
    void getPermissionListReturnsKeysOfUser() {
        when(permissionService.listKeysByUserId(1)).thenReturn(List.of("system:user:list"));

        assertThat(stpInterface.getPermissionList("1", "login")).containsExactly("system:user:list");
    }

    @Test
    void getPermissionListReturnsEmptyWhenLookupFails() {
        when(permissionService.listKeysByUserId(1)).thenThrow(new IllegalStateException("db down"));

        assertThat(stpInterface.getPermissionList("1", "login")).isEmpty();
    }
}
