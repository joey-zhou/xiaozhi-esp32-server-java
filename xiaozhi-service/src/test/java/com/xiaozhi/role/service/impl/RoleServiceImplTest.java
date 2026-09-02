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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住角色查询与「复制默认角色」的不变量：
 * 列表查询必须带 userId + 启用状态 + LIMIT，复制前先把目标用户原有的默认角色降级。
 */
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

    @Test
    void listBOReturnsEmptyWhenInputInvalid() {
        assertThat(roleService.listBO(null, 5)).isEmpty();
        assertThat(roleService.listBO(1, 0)).isEmpty();

        verifyNoInteractions(roleMapper, roleConvert);
    }

    @Test
    void listBOQueriesEnabledRolesOfUserWithLimit() {
        RoleDO roleDO = newRoleDO(1);
        RoleBO roleBO = newRoleBO(1);

        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleDO));
        when(roleConvert.toBO(roleDO)).thenReturn(roleBO);

        List<RoleBO> result = roleService.listBO(7, 3);

        assertThat(result).containsExactly(roleBO);

        ArgumentCaptor<LambdaQueryWrapper<RoleDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("userId =")
            .contains("state =")
            .contains("LIMIT 3");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(7, "1");
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
        RoleDO roleDO = newRoleDO(9);
        RoleBO roleBO = newRoleBO(9);

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(roleDO);
        when(roleConvert.toBO(roleDO)).thenReturn(roleBO);

        RoleBO result = roleService.getDefaultOrFirstBO(7);

        assertThat(result).isSameAs(roleBO);
    }

    @Test
    void copyDefaultRoleCopiesSourceRoleForTargetUser() {
        RoleDO sourceRole = newRoleDO(1);
        RoleDO copiedRole = newRoleDO(22);
        RoleBO copiedRoleBO = newRoleBO(22);

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sourceRole);
        when(roleConvert.copy(sourceRole)).thenReturn(copiedRole);
        when(roleMapper.insert(copiedRole)).thenReturn(1);
        when(roleMapper.selectById(22)).thenReturn(copiedRole);
        when(roleConvert.toBO(copiedRole)).thenReturn(copiedRoleBO);
        stubCacheHelperLoadingFromDb();

        Integer result = roleService.copyDefaultRole(10, 20);

        assertThat(result).isEqualTo(22);
        assertThat(copiedRole.getUserId()).isEqualTo(20);
        assertThat(copiedRole.getIsDefault()).isEqualTo("1");
        verify(roleMapper).insert(copiedRole);
        // 复制前必须先把目标用户原有的默认角色降级，避免出现两个默认角色
        ArgumentCaptor<LambdaUpdateWrapper<RoleDO>> resetCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(roleMapper).update(isNull(), resetCaptor.capture());
        assertThat(resetCaptor.getValue().getSqlSet()).contains("isDefault=");
        assertThat(resetCaptor.getValue().getTargetSql()).contains("userId =");
        assertThat(resetCaptor.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(20, "0");
    }

    @Test
    void copyDefaultRoleThrowsWhenInsertFails() {
        RoleDO sourceRole = newRoleDO(1);
        RoleDO copiedRole = newRoleDO(22);

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
        RoleDO sourceRole = newRoleDO(1);
        RoleDO copiedRole = newRoleDO(22);

        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sourceRole);
        when(roleConvert.copy(sourceRole)).thenReturn(copiedRole);
        when(roleMapper.insert(copiedRole)).thenReturn(1);
        when(roleMapper.selectById(22)).thenReturn(null);
        stubCacheHelperLoadingFromDb();

        assertThatThrownBy(() -> roleService.copyDefaultRole(10, 20))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("复制默认角色失败");
    }

    /** getBO 走缓存包装，测试里让它直接执行回源逻辑。 */
    private void stubCacheHelperLoadingFromDb() {
        when(cacheHelper.getWithLock(any(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
    }

    private static RoleDO newRoleDO(Integer roleId) {
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleId(roleId);
        return roleDO;
    }

    private static RoleBO newRoleBO(Integer roleId) {
        RoleBO roleBO = new RoleBO();
        roleBO.setRoleId(roleId);
        return roleBO;
    }
}
