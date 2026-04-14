package com.xiaozhi.userauth.service.impl;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.UserAuthBO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.userauth.dal.mysql.dataobject.UserAuthDO;
import com.xiaozhi.userauth.dal.mysql.mapper.UserAuthMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(UserAuthDO.class);
    }

    @Mock
    private UserAuthMapper userAuthMapper;

    @InjectMocks
    private UserAuthServiceImpl userAuthService;

    @Test
    void getByOpenIdAndPlatformReturnsNullWhenInputInvalid() {
        assertThat(userAuthService.getByOpenIdAndPlatform(" ", "wechat")).isNull();
        assertThat(userAuthService.getByUserIdAndPlatform(null, "wechat")).isNull();
    }

    @Test
    void createThrowsWhenUserAuthIncomplete() {
        assertThatThrownBy(() -> userAuthService.create(new UserAuthBO()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("用户授权信息不完整");
    }

    @Test
    void createReturnsPersistedUserAuth() {
        UserAuthBO userAuth = new UserAuthBO();
        userAuth.setUserId(1);
        userAuth.setOpenId("openid");
        userAuth.setPlatform("wechat");

        UserAuthDO persisted = new UserAuthDO();
        persisted.setId(8L);
        persisted.setUserId(1);
        persisted.setOpenId("openid");
        persisted.setPlatform("wechat");

        when(userAuthMapper.insert(org.mockito.ArgumentMatchers.any(UserAuthDO.class))).thenAnswer(invocation -> {
            UserAuthDO arg = invocation.getArgument(0);
            arg.setId(8L);
            return 1;
        });
        when(userAuthMapper.selectById(8L)).thenReturn(persisted);

        UserAuthBO result = userAuthService.create(userAuth);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(8L);
        assertThat(result.getUserId()).isEqualTo(1);
        assertThat(result.getOpenId()).isEqualTo("openid");
    }

    @Test
    void updateThrowsWhenUserAuthMissing() {
        UserAuthBO userAuth = new UserAuthBO();
        userAuth.setId(1L);
        when(userAuthMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> userAuthService.update(userAuth))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("用户授权不存在");
    }

    @Test
    void deleteByIdThrowsWhenIdMissing() {
        assertThatThrownBy(() -> userAuthService.deleteById(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("授权ID不能为空");
    }
}
