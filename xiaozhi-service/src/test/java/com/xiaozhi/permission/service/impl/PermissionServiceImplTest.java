package com.xiaozhi.permission.service.impl;

import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.common.model.bo.PermissionBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.permission.convert.PermissionConvert;
import com.xiaozhi.permission.dal.mysql.dataobject.PermissionDO;
import com.xiaozhi.permission.dal.mysql.mapper.PermissionMapper;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.user.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(PermissionDO.class, AuthRolePermissionDO.class);
    }

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private AuthRolePermissionMapper authRolePermissionMapper;

    @Mock
    private UserService userService;

    @Mock
    private PermissionConvert permissionConvert;

    @Mock
    private PermissionService self;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @BeforeEach
    void injectSelf() throws Exception {
        Field selfField = PermissionServiceImpl.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(permissionService, self);
    }

    private static PermissionBO permission(Integer permissionId, Integer parentId, String permissionKey) {
        PermissionBO permission = new PermissionBO();
        permission.setPermissionId(permissionId);
        permission.setParentId(parentId);
        permission.setPermissionKey(permissionKey);
        return permission;
    }

    private static UserBO userWithAuthRole(Integer authRoleId) {
        UserBO user = new UserBO();
        user.setAuthRoleId(authRoleId);
        return user;
    }

    @Test
    void listByAuthRoleIdReturnsEmptyWhenAuthRoleIdMissing() {
        assertThat(permissionService.listByAuthRoleId(null)).isEmpty();
        verifyNoInteractions(permissionMapper, authRolePermissionMapper, userService, permissionConvert);
    }

    @Test
    void listIdsByAuthRoleIdDeduplicatesPermissionIds() {
        AuthRolePermissionDO one = new AuthRolePermissionDO();
        one.setPermissionId(10);
        AuthRolePermissionDO duplicate = new AuthRolePermissionDO();
        duplicate.setPermissionId(10);
        AuthRolePermissionDO two = new AuthRolePermissionDO();
        two.setPermissionId(20);

        when(authRolePermissionMapper.selectList(any())).thenReturn(List.of(one, duplicate, two));

        List<Integer> result = permissionService.listIdsByAuthRoleId(1);

        assertThat(result).containsExactly(10, 20);
    }

    @Test
    void listByUserIdReturnsPermissionsForUserRole() {
        PermissionBO permission = permission(10, 0, "system:user:list");

        when(userService.getBO(1)).thenReturn(userWithAuthRole(2));
        when(self.listByAuthRoleId(2)).thenReturn(List.of(permission));

        List<PermissionBO> result = permissionService.listByUserId(1);

        assertThat(result).containsExactly(permission);
        verify(userService).getBO(1);
    }

    @Test
    void listByUserIdReturnsEmptyWhenUserHasNoAuthRole() {
        when(userService.getBO(1)).thenReturn(userWithAuthRole(null));

        assertThat(permissionService.listByUserId(1)).isEmpty();
        verifyNoInteractions(self);
    }

    @Test
    void listKeysByUserIdSkipsBlankKeys() {
        when(userService.getBO(1)).thenReturn(userWithAuthRole(2));
        when(self.listByAuthRoleId(2)).thenReturn(List.of(
            permission(10, 0, "system:user:list"),
            permission(11, 0, null),
            permission(12, 0, " "),
            permission(13, 0, "system:user:detail")));

        List<String> result = permissionService.listKeysByUserId(1);

        assertThat(result).containsExactly("system:user:list", "system:user:detail");
    }

    @Test
    void listTreeBuildsParentChildStructure() {
        PermissionDO parentDO = new PermissionDO();
        parentDO.setPermissionId(1);
        PermissionDO childDO = new PermissionDO();
        childDO.setPermissionId(2);
        PermissionBO parent = permission(1, 0, "system");
        PermissionBO child = permission(2, 1, "system:user");

        when(permissionMapper.selectList(any())).thenReturn(List.of(parentDO, childDO));
        when(permissionConvert.toBO(parentDO)).thenReturn(parent);
        when(permissionConvert.toBO(childDO)).thenReturn(child);

        List<PermissionBO> result = permissionService.listTree();

        assertThat(result).containsExactly(parent);
        assertThat(parent.getChildren()).containsExactly(child);
        assertThat(child.getChildren()).isEmpty();
    }

    @Test
    void listTreeByUserIdTreatsOrphanAsRoot() {
        PermissionBO orphan = permission(5, 99, "system:orphan");
        PermissionBO root = permission(1, null, "system");

        when(userService.getBO(1)).thenReturn(userWithAuthRole(2));
        when(self.listByAuthRoleId(2)).thenReturn(List.of(orphan, root));

        List<PermissionBO> result = permissionService.listTreeByUserId(1);

        assertThat(result).containsExactly(orphan, root);
    }
}
