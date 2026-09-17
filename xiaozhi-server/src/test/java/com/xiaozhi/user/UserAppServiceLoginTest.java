package com.xiaozhi.user;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.UserAuthBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.port.DeviceWriter;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.security.service.AuthenticationService;
import com.xiaozhi.template.service.TemplateService;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.userauth.service.UserAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 手机号与微信登录的自动建号编排。
 * <p>
 * 这两段原来写在 Controller 里，微信那条还跨了两个事务：建号先提交、写授权记录再来一次，
 * 中间失败就留下一个建好的用户但没有任何 openId 指向它——同一个微信号下次登录只会又建一个新账号，
 * 之前那个连同角色、虚拟设备、聊天记录再也回不去，只能人工清库。
 * 下沉到 AppService 后靠 {@code @Transactional} 保证原子，本类钉住编排本身的分支。
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceLoginTest {

    @Mock
    private UserService userService;
    @Mock
    private RoleService roleService;
    @Mock
    private TemplateService templateService;
    @Mock
    private DeviceWriter deviceWriter;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private UserAuthService userAuthService;

    private UserAppService userAppService;

    @BeforeEach
    void setUp() {
        userAppService = new UserAppService();
        ReflectionTestUtils.setField(userAppService, "userService", userService);
        ReflectionTestUtils.setField(userAppService, "roleService", roleService);
        ReflectionTestUtils.setField(userAppService, "templateService", templateService);
        ReflectionTestUtils.setField(userAppService, "deviceWriter", deviceWriter);
        ReflectionTestUtils.setField(userAppService, "authenticationService", authenticationService);
        ReflectionTestUtils.setField(userAppService, "userAuthService", userAuthService);
        lenient().when(authenticationService.encryptPassword(anyString())).thenReturn("encrypted");
    }

    private void stubUserCreation(int userId) {
        UserBO created = new UserBO();
        created.setUserId(userId);
        when(userService.create(any(UserBO.class))).thenReturn(created);
        when(roleService.copyDefaultRole(1, userId)).thenReturn(11);
    }

    // ==================== 手机号 ====================

    @Test
    void telLoginReusesTheExistingAccount() {
        UserBO existing = new UserBO();
        existing.setUserId(7);
        when(userService.getByTel("13800000000")).thenReturn(existing);

        UserBO user = userAppService.loginByTel("13800000000");

        assertThat(user).isSameAs(existing);
        verify(userService).requireEnabled(existing);
        verify(userService, never()).create(any());
    }

    @Test
    void telLoginRefusesADisabledAccountBeforeDoingAnythingElse() {
        UserBO existing = new UserBO();
        existing.setUserId(7);
        when(userService.getByTel("13800000000")).thenReturn(existing);
        doThrow(new UnauthorizedException("账号已被禁用")).when(userService).requireEnabled(existing);

        assertThatThrownBy(() -> userAppService.loginByTel("13800000000"))
            .isInstanceOf(UnauthorizedException.class);

        verify(userService, never()).create(any());
    }

    @Test
    void telLoginCreatesAnAccountWithItsDefaultResources() {
        when(userService.getByTel("13800000000")).thenReturn(null);
        stubUserCreation(7);

        UserBO user = userAppService.loginByTel("13800000000");

        assertThat(user.getUserId()).isEqualTo(7);
        ArgumentCaptor<UserBO> captor = ArgumentCaptor.forClass(UserBO.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getTel()).isEqualTo("13800000000");
        assertThat(captor.getValue().getUsername()).startsWith("tel_0000_");
        assertThat(captor.getValue().getName()).isEqualTo("用户0000");
        assertThat(captor.getValue().getPassword())
            .as("自动建的号也必须有一份加密后的随机口令，不能留空")
            .isEqualTo("encrypted");

        verify(templateService).copyTemplates(1, 7);
        verify(deviceWriter).register("user_chat_7", "网页聊天", "web", 7, 11);
    }

    @Test
    void telLoginHandlesANumberShorterThanFourDigits() {
        when(userService.getByTel("123")).thenReturn(null);
        stubUserCreation(7);

        userAppService.loginByTel("123");

        ArgumentCaptor<UserBO> captor = ArgumentCaptor.forClass(UserBO.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("用户123");
    }

    // ==================== 微信 ====================

    @Test
    void wechatLoginReusesTheAccountBehindAKnownOpenId() {
        UserAuthBO auth = new UserAuthBO();
        auth.setUserId(7);
        UserBO existing = new UserBO();
        existing.setUserId(7);
        when(userAuthService.getByOpenIdAndPlatform("open-1", "wechat")).thenReturn(auth);
        when(userService.getBO(7)).thenReturn(existing);

        UserAppService.WechatLogin result = userAppService.loginByWechat("open-1", "union-1", "{}");

        assertThat(result.user()).isSameAs(existing);
        assertThat(result.newUser()).isFalse();
        verify(userService).requireEnabled(existing);
        verify(userService, never()).create(any());
        verify(userAuthService, never()).create(any());
    }

    @Test
    void wechatLoginFailsLoudlyWhenTheAuthRowPointsAtAMissingUser() {
        UserAuthBO auth = new UserAuthBO();
        auth.setUserId(7);
        when(userAuthService.getByOpenIdAndPlatform("open-1", "wechat")).thenReturn(auth);
        when(userService.getBO(7)).thenReturn(null);

        assertThatThrownBy(() -> userAppService.loginByWechat("open-1", "union-1", "{}"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void wechatLoginCreatesTheAccountAndItsAuthRowTogether() {
        when(userAuthService.getByOpenIdAndPlatform("open-1", "wechat")).thenReturn(null);
        stubUserCreation(7);

        UserAppService.WechatLogin result = userAppService.loginByWechat("open-1", "union-1", "{\"n\":1}");

        assertThat(result.user().getUserId()).isEqualTo(7);
        assertThat(result.newUser()).isTrue();

        ArgumentCaptor<UserAuthBO> captor = ArgumentCaptor.forClass(UserAuthBO.class);
        verify(userAuthService).create(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7);
        assertThat(captor.getValue().getOpenId()).isEqualTo("open-1");
        assertThat(captor.getValue().getUnionId()).isEqualTo("union-1");
        assertThat(captor.getValue().getPlatform()).isEqualTo("wechat");
        assertThat(captor.getValue().getProfile()).isEqualTo("{\"n\":1}");

        verify(deviceWriter).register("user_chat_7", "网页聊天", "web", 7, 11);
    }

    @Test
    void aFailingAuthRowWriteAbortsTheWholeRegistration() {
        when(userAuthService.getByOpenIdAndPlatform("open-1", "wechat")).thenReturn(null);
        stubUserCreation(7);
        when(userAuthService.create(any())).thenThrow(new IllegalStateException("唯一键冲突"));

        assertThatThrownBy(() -> userAppService.loginByWechat("open-1", "union-1", "{}"))
            .as("异常必须原样抛出去让事务回滚，吞掉就会留下一个没有 openId 指向的孤儿账号")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wechatUsernameStaysWithinTenCharactersOfTheOpenId() {
        when(userAuthService.getByOpenIdAndPlatform(anyString(), anyString())).thenReturn(null);
        stubUserCreation(7);

        userAppService.loginByWechat("0123456789abcdefghij", null, "{}");

        ArgumentCaptor<UserBO> captor = ArgumentCaptor.forClass(UserBO.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("wx_0123456789");
    }

    @Test
    void shortOpenIdsDoNotBlowUpTheUsername() {
        when(userAuthService.getByOpenIdAndPlatform(anyString(), anyString())).thenReturn(null);
        stubUserCreation(7);

        userAppService.loginByWechat("ab", null, "{}");

        ArgumentCaptor<UserBO> captor = ArgumentCaptor.forClass(UserBO.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("wx_ab");
        verify(roleService).copyDefaultRole(1, 7);
    }
}
