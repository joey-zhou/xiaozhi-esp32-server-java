package com.xiaozhi.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import com.xiaozhi.user.dal.mysql.mapper.UserMapper;
import com.xiaozhi.user.model.UserProjection;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住建号时的默认值补齐（启用状态、非管理员、后台权限角色）与唯一性校验：
 * sys_user.authRoleId 存的是后台权限角色，不是对话 persona。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(UserDO.class);
    }

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserConvert userConvert;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void pageReturnsProjectionRecordsUntouched() {
        UserProjection projection = new UserProjection();
        projection.setUserId(10);
        projection.setTel("138****1234");

        Page<UserProjection> page = new Page<>(2, 5);
        page.setRecords(List.of(projection));
        page.setTotal(8);

        when(userMapper.selectPage(any(Page.class), eq("ali"), isNull(), isNull(), isNull(), eq(2)))
            .thenReturn(page);

        PageResult<UserProjection> result = userService.page(2, 5, "ali", null, null, null, 2);

        assertThat(result.getList()).containsExactly(projection);
        assertThat(result.getTotal()).isEqualTo(8);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
    }

    @Test
    void createPersistsUserAndReturnsBO() {
        UserBO draft = new UserBO();
        draft.setUsername("alice");
        draft.setPassword("encoded");
        draft.setEmail("alice@example.com");

        UserDO createdDO = new UserDO();
        UserDO persistedDO = new UserDO();
        persistedDO.setUserId(99);
        UserBO persistedBO = new UserBO();
        persistedBO.setUserId(99);
        persistedBO.setUsername("alice");

        when(userMapper.selectOne(any())).thenReturn(null, null);
        when(userConvert.toDO(draft)).thenReturn(createdDO);
        when(userConvert.toBO(nullable(UserDO.class))).thenAnswer(invocation -> {
            UserDO arg = invocation.getArgument(0);
            return arg == persistedDO ? persistedBO : null;
        });
        when(userMapper.insert(createdDO)).thenAnswer(invocation -> {
            createdDO.setUserId(99);
            return 1;
        });
        when(userMapper.selectById(99)).thenReturn(persistedDO);

        UserBO result = userService.create(draft);

        assertThat(result).isSameAs(persistedBO);
        verify(userMapper).insert(createdDO);
        // createdDO 由生产代码就地补默认值：启用、非管理员、后台权限角色 2
        assertThat(createdDO.getState()).isEqualTo(UserBO.STATE_ENABLED);
        assertThat(createdDO.getIsAdmin()).isEqualTo(UserBO.ADMIN_NO);
        assertThat(createdDO.getAuthRoleId()).isEqualTo(2);
    }

    @Test
    void createThrowsWhenUsernameAlreadyExists() {
        UserBO draft = new UserBO();
        draft.setUsername("alice");
        draft.setPassword("encoded");

        UserDO existingDO = new UserDO();
        UserBO existingBO = new UserBO();
        existingBO.setUserId(1);

        when(userMapper.selectOne(any())).thenReturn(existingDO);
        when(userConvert.toBO(any(UserDO.class))).thenAnswer(invocation -> invocation.getArgument(0) == existingDO ? existingBO : null);

        assertThatThrownBy(() -> userService.create(draft))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("用户名已存在");
    }

    @Test
    void updateReturnsThePersistedRowSoCallersNeedNoSecondQuery() {
        UserBO patch = new UserBO();
        patch.setUserId(7);
        patch.setName("新名字");

        UserDO stored = new UserDO();
        stored.setUserId(7);
        UserBO persisted = new UserBO();
        persisted.setUserId(7);
        persisted.setName("新名字");

        when(userMapper.selectById(7)).thenReturn(stored);
        when(userMapper.updateById(stored)).thenReturn(1);
        when(userConvert.toBO(stored)).thenReturn(persisted);

        UserBO result = userService.update(patch);

        // 出参就是「库里那行 + 本次改动」，调用方不必写完再查一遍
        assertThat(result).isSameAs(persisted);
        verify(userMapper).selectById(7);
        verify(userConvert).updateDO(patch, stored);
    }

    @Test
    void updateThrowsWhenUserNotFound() {
        UserBO user = new UserBO();
        user.setUserId(999);

        when(userMapper.selectById(999)).thenReturn(null);

        assertThatThrownBy(() -> userService.update(user))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("用户不存在");
    }

    @Test
    void requireEnabledRejectsDisabledUserOnly() {
        UserBO disabled = new UserBO();
        disabled.setState(UserBO.STATE_DISABLED);

        assertThatThrownBy(() -> userService.requireEnabled(disabled))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("账号已被禁用");

        UserBO enabled = new UserBO();
        enabled.setState(UserBO.STATE_ENABLED);
        userService.requireEnabled(enabled);
        userService.requireEnabled(null);
    }
}
