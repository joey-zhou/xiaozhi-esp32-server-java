package com.xiaozhi.permission.service.impl;

import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.resp.PermissionResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
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
        UserBO user = new UserBO();
        user.setAuthRoleId(2);
        PermissionResp permissionResp = new PermissionResp();
        permissionResp.setPermissionId(10);

        when(userService.getBO(1)).thenReturn(user);
        when(self.listByAuthRoleId(2)).thenReturn(List.of(permissionResp));

        List<PermissionResp> result = permissionService.listByUserId(1);

        assertThat(result).containsExactly(permissionResp);
        verify(userService).getBO(1);
    }

    @Test
    void listTreeBuildsParentChildStructure() {
        PermissionDO parentDO = new PermissionDO();
        parentDO.setPermissionId(1);
        PermissionDO childDO = new PermissionDO();
        childDO.setPermissionId(2);

        PermissionResp parentResp = new PermissionResp();
        parentResp.setPermissionId(1);
        parentResp.setParentId(0);
        PermissionResp childResp = new PermissionResp();
        childResp.setPermissionId(2);
        childResp.setParentId(1);

        PermissionTreeResp parentTree = new PermissionTreeResp();
        parentTree.setPermissionId(1);
        parentTree.setParentId(0);
        PermissionTreeResp childTree = new PermissionTreeResp();
        childTree.setPermissionId(2);
        childTree.setParentId(1);

        when(permissionMapper.selectList(any())).thenReturn(List.of(parentDO, childDO));
        when(permissionConvert.toResp(parentDO)).thenReturn(parentResp);
        when(permissionConvert.toResp(childDO)).thenReturn(childResp);
        when(permissionConvert.toTreeResp(parentResp)).thenReturn(parentTree);
        when(permissionConvert.toTreeResp(childResp)).thenReturn(childTree);

        List<PermissionTreeResp> result = permissionService.listTree();

        assertThat(result).containsExactly(parentTree);
        assertThat(parentTree.getChildren()).containsExactly(childTree);
    }
}
