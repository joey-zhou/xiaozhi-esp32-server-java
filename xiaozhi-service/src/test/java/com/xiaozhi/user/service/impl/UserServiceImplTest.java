package com.xiaozhi.user.service.impl;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import com.xiaozhi.user.dal.mysql.mapper.UserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createWithBOPersistsUserAndReturnsBO() {
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
    }

    @Test
    void createWithBOThrowsWhenUsernameAlreadyExists() {
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
    void updateThrowsWhenUserNotFound() {
        UserBO user = new UserBO();
        user.setUserId(999);

        when(userMapper.selectById(999)).thenReturn(null);

        assertThatThrownBy(() -> userService.update(user))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("用户不存在");
    }

    @Test
    void generateCaptchaThrowsWhenAccountBlank() {
        assertThatThrownBy(() -> userService.generateCaptcha(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("账号不能为空");
    }
}
