package com.xiaozhi.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.role.convert.RoleConvert;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.dal.mysql.mapper.RoleMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(RoleDO.class);
    }

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RoleConvert roleConvert;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private CacheHelper cacheHelper;

    @InjectMocks
    private RoleServiceImpl roleService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        lenient().when(cacheHelper.getWithLock(any(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());
    }

    @Test
    void listBOReturnsEmptyWhenInputInvalid() {
        assertThat(roleService.listBO(null, 5)).isEmpty();
        assertThat(roleService.listBO(1, 0)).isEmpty();

        verifyNoInteractions(roleMapper, roleConvert);
    }

    @Test
    void listBOReturnsMappedRolesWhenInputValid() {
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleId(1);
        RoleBO roleBO = new RoleBO();
        roleBO.setRoleId(1);

        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleDO));
        when(roleConvert.toBO(roleDO)).thenReturn(roleBO);

        List<RoleBO> result = roleService.listBO(7, 3);

        assertThat(result).containsExactly(roleBO);
        verify(roleMapper).selectList(any(LambdaQueryWrapper.class));
        verify(roleConvert).toBO(roleDO);
    }

    @Test
    void pageReturnsMapperResult() {
        RoleResp roleResp = new RoleResp();
        roleResp.setRoleId(10);

        Page<RoleResp> page = new Page<>(2, 5);
        page.setRecords(List.of(roleResp));
        page.setTotal(8);

        when(roleMapper.selectPageResp(any(Page.class), isNull(), isNull(), isNull(), isNull(), eq(7))).thenReturn(page);

        var result = roleService.page(2, 5, null, null, null, null, 7);

        assertThat(result.getList()).containsExactly(roleResp);
        assertThat(result.getTotal()).isEqualTo(8);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
    }

    @Test
    void getDefaultOrFirstBOReturnsMappedRole() {
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleId(9);
        RoleBO roleBO = new RoleBO();
        roleBO.setRoleId(9);

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(roleDO);
        when(roleConvert.toBO(roleDO)).thenReturn(roleBO);

        RoleBO result = roleService.getDefaultOrFirstBO(7);

        assertThat(result).isSameAs(roleBO);
    }

    @Test
    void copyDefaultRoleCopiesSourceRoleForTargetUser() {
        RoleDO sourceRole = new RoleDO();
        sourceRole.setRoleId(1);
        sourceRole.setUserId(10);
        sourceRole.setIsDefault("1");

        RoleDO copiedRole = new RoleDO();
        copiedRole.setRoleId(22);

        RoleBO copiedRoleBO = new RoleBO();
        copiedRoleBO.setRoleId(22);

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sourceRole);
        when(roleConvert.copy(sourceRole)).thenReturn(copiedRole);
        when(roleMapper.insert(copiedRole)).thenReturn(1);
        when(roleMapper.selectById(22)).thenReturn(copiedRole);
        when(roleConvert.toBO(copiedRole)).thenReturn(copiedRoleBO);

        Integer result = roleService.copyDefaultRole(10, 20);

        assertThat(result).isEqualTo(22);
        assertThat(copiedRole.getUserId()).isEqualTo(20);
        assertThat(copiedRole.getIsDefault()).isEqualTo("1");
        verify(roleMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(roleMapper).insert(copiedRole);
    }

    @Test
    void copyDefaultRoleThrowsWhenInsertFails() {
        RoleDO sourceRole = new RoleDO();
        RoleDO copiedRole = new RoleDO();

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sourceRole);
        when(roleConvert.copy(sourceRole)).thenReturn(copiedRole);
        when(roleMapper.insert(copiedRole)).thenReturn(0);

        assertThatThrownBy(() -> roleService.copyDefaultRole(10, 20))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("复制默认角色失败");
    }

    @Test
    void copyDefaultRoleThrowsWhenSourceRoleMissing() {
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> roleService.copyDefaultRole(10, 20))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("默认角色模板不存在");
    }

    @Test
    void copyDefaultRoleThrowsWhenCopiedRoleCannotBeLoaded() {
        RoleDO sourceRole = new RoleDO();
        sourceRole.setRoleId(1);

        RoleDO copiedRole = new RoleDO();
        copiedRole.setRoleId(22);

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sourceRole);
        when(roleConvert.copy(sourceRole)).thenReturn(copiedRole);
        when(roleMapper.insert(copiedRole)).thenReturn(1);
        when(roleMapper.selectById(22)).thenReturn(null);

        assertThatThrownBy(() -> roleService.copyDefaultRole(10, 20))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("复制默认角色失败");
    }

}
