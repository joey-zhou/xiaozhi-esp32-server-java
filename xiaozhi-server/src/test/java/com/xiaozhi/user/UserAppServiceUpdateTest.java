package com.xiaozhi.user;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserUpdateReq;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住改资料与改状态的出参：取 update 落库后返回的那份用户信息，写完不再回查用户表。
 * <p>user 包没有 domain/（规约 §9 第 1 条判定为数据档），出参改由 Service 的写方法直接给出，
 * 与 template 降级包的做法一致。
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceUpdateTest {

    private static final int USER_ID = 7;
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationService authenticationService;

    private final UserConvert userConvert = Mappers.getMapper(UserConvert.class);

    private UserAppService userAppService;

    @BeforeEach
    void setUp() {
        userAppService = new UserAppService();
        ReflectionTestUtils.setField(userAppService, "userService", userService);
        ReflectionTestUtils.setField(userAppService, "userConvert", userConvert);
        ReflectionTestUtils.setField(userAppService, "authenticationService", authenticationService);
    }

    @Test
    void updateReturnsEveryFieldOfThePersistedUser() {
        when(userService.getBO(USER_ID)).thenReturn(storedUser());
        when(userService.update(any(UserBO.class))).thenAnswer(invocation -> {
            UserBO patched = invocation.getArgument(0, UserBO.class);
            patched.setUpdateTime(UPDATED_AT);
            return patched;
        });

        UserUpdateReq req = new UserUpdateReq();
        req.setName("新名字");

        UserResp resp = userAppService.update(USER_ID, req);

        assertThat(resp.getUserId()).isEqualTo(USER_ID);
        assertThat(resp.getName()).isEqualTo("新名字");
        // 本次没带的字段仍是库里那份，不能因为不回读就丢
        assertThat(resp.getUsername()).isEqualTo("alice");
        assertThat(resp.getEmail()).isEqualTo("alice@example.com");
        assertThat(resp.getTel()).isEqualTo("13800000000");
        assertThat(resp.getAvatar()).isEqualTo("avatar/user.png");
        assertThat(resp.getState()).isEqualTo(UserBO.STATE_ENABLED);
        assertThat(resp.getIsAdmin()).isEqualTo(UserBO.ADMIN_NO);
        assertThat(resp.getAuthRoleId()).isEqualTo(2);
        assertThat(resp.getLoginIp()).isEqualTo("10.0.0.8");
        assertThat(resp.getUpdateTime()).isEqualTo(UPDATED_AT);
        // JOIN 出来的统计列本来就不在这个接口的返回里
        assertThat(resp.getAuthRoleName()).isNull();
        assertThat(resp.getTotalMessage()).isNull();
        assertThat(resp.getTotalDevice()).isNull();
        assertThat(resp.getAliveNumber()).isNull();
        // 用户表只在合并前读了一次，写完不再回读
        verify(userService, times(1)).getBO(USER_ID);
    }

    @Test
    void updateStateReturnsThePersistedUser() {
        when(userService.getBO(USER_ID)).thenReturn(storedUser());
        when(userService.update(any(UserBO.class))).thenAnswer(invocation -> {
            UserBO persisted = storedUser();
            persisted.setState(UserBO.STATE_DISABLED);
            persisted.setUpdateTime(UPDATED_AT);
            return persisted;
        });

        UserResp resp = userAppService.updateState(USER_ID, UserBO.STATE_DISABLED);

        assertThat(resp.getUserId()).isEqualTo(USER_ID);
        assertThat(resp.getState()).isEqualTo(UserBO.STATE_DISABLED);
        // 只改状态也要返回完整资料，前端拿它直接刷新列表行
        assertThat(resp.getUsername()).isEqualTo("alice");
        assertThat(resp.getName()).isEqualTo("爱丽丝");
        assertThat(resp.getUpdateTime()).isEqualTo(UPDATED_AT);
        verify(userService, times(1)).getBO(USER_ID);
    }

    @Test
    void updateStateRejectsUnknownAccountBeforeWriting() {
        when(userService.getBO(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> userAppService.updateState(USER_ID, UserBO.STATE_DISABLED))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("无此用户，更新失败");

        verify(userService, never()).update(any());
    }

    private static UserBO storedUser() {
        UserBO user = new UserBO();
        user.setUserId(USER_ID);
        user.setUsername("alice");
        user.setName("爱丽丝");
        user.setEmail("alice@example.com");
        user.setTel("13800000000");
        user.setAvatar("avatar/user.png");
        user.setState(UserBO.STATE_ENABLED);
        user.setIsAdmin(UserBO.ADMIN_NO);
        user.setAuthRoleId(2);
        user.setLoginIp("10.0.0.8");
        user.setPassword("encrypted");
        return user;
    }
}
