package com.xiaozhi.security.ownership;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.dal.mysql.mapper.RoleMapper;
import com.xiaozhi.template.dal.mysql.dataobject.TemplateDO;
import com.xiaozhi.template.dal.mysql.mapper.TemplateMapper;
import com.xiaozhi.user.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 归属校验器是普通用户之间唯一的横向越权防线。
 * 每个 checker 钉三条：资源名（改了名注解就找不到 checker）、
 * 资源不存在抛 404、归属不符抛 403——任一条漏掉都会变成放行。
 */
class OwnershipConfigTest {

    private static final Integer OWNER = 7;
    private static final Integer OTHER = 8;

    private final OwnershipConfig config = new OwnershipConfig();

    /** 全部 checker 的资源名不能重名，重名会让后注册的把前一个顶掉，整类资源失去校验。 */
    @Test
    void resourceNamesAreUnique() {
        List<String> resources = List.of(
            config.roleOwnershipChecker(mock(RoleMapper.class)).getResource(),
            config.configOwnershipChecker(mock(ConfigService.class)).getResource(),
            config.templateOwnershipChecker(mock(TemplateMapper.class)).getResource(),
            config.deviceOwnershipChecker(mock(DeviceService.class)).getResource(),
            config.messageOwnershipChecker(mock(MessageService.class)).getResource(),
            config.userOwnershipChecker(mock(UserService.class)).getResource());

        assertThat(resources).doesNotHaveDuplicates()
            .containsExactly("role", "config", "template", "device", "message", "user");
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class RoleChecker {

        @Mock
        private RoleMapper roleMapper;

        @Test
        void resourceNameIsRole() {
            assertThat(config.roleOwnershipChecker(roleMapper).getResource()).isEqualTo("role");
        }

        @Test
        void passesWhenOwnedByUser() {
            when(roleMapper.selectById(3)).thenReturn(role(OWNER));

            assertThatCode(() -> config.roleOwnershipChecker(roleMapper).check(3, OWNER))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsMissingRoleAsNotFound() {
            when(roleMapper.selectById(3)).thenReturn(null);

            assertThatThrownBy(() -> config.roleOwnershipChecker(roleMapper).check("3", OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在");
        }

        @Test
        void rejectsOtherUsersRoleAsUnauthorized() {
            when(roleMapper.selectById(3)).thenReturn(role(OWNER));

            assertThatThrownBy(() -> config.roleOwnershipChecker(roleMapper).check(3, OTHER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("角色不归属当前用户");
        }

        /** 归属列为 null 的历史数据不能被当作「谁都能改」。 */
        @Test
        void rejectsRoleWithoutOwnerAsUnauthorized() {
            when(roleMapper.selectById(3)).thenReturn(role(null));

            assertThatThrownBy(() -> config.roleOwnershipChecker(roleMapper).check(3, OWNER))
                .isInstanceOf(UnauthorizedException.class);
        }

        private RoleDO role(Integer userId) {
            RoleDO role = new RoleDO();
            role.setRoleId(3);
            role.setUserId(userId);
            return role;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ConfigChecker {

        @Mock
        private ConfigService configService;

        @Test
        void resourceNameIsConfig() {
            assertThat(config.configOwnershipChecker(configService).getResource()).isEqualTo("config");
        }

        @Test
        void passesWhenOwnedByUser() {
            when(configService.getBO(3)).thenReturn(configBO(OWNER));

            assertThatCode(() -> config.configOwnershipChecker(configService).check(3, OWNER))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsMissingConfigAsNotFound() {
            when(configService.getBO(3)).thenReturn(null);

            assertThatThrownBy(() -> config.configOwnershipChecker(configService).check(3, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("配置不存在");
        }

        @Test
        void rejectsOtherUsersConfigAsUnauthorized() {
            when(configService.getBO(3)).thenReturn(configBO(OWNER));

            assertThatThrownBy(() -> config.configOwnershipChecker(configService).check(3, OTHER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("配置不归属当前用户");
        }

        private ConfigBO configBO(Integer userId) {
            ConfigBO configBO = new ConfigBO();
            configBO.setConfigId(3);
            configBO.setUserId(userId);
            return configBO;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class TemplateChecker {

        @Mock
        private TemplateMapper templateMapper;

        @Test
        void resourceNameIsTemplate() {
            assertThat(config.templateOwnershipChecker(templateMapper).getResource()).isEqualTo("template");
        }

        @Test
        void passesWhenOwnedByUser() {
            when(templateMapper.selectById(3)).thenReturn(template(OWNER, TemplateBO.STATE_ENABLED));

            assertThatCode(() -> config.templateOwnershipChecker(templateMapper).check(3, OWNER))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsMissingTemplateAsNotFound() {
            when(templateMapper.selectById(3)).thenReturn(null);

            assertThatThrownBy(() -> config.templateOwnershipChecker(templateMapper).check(3, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("模板不存在");
        }

        /** 已停用的模板与不存在等价，否则删除后仍可被引用。 */
        @Test
        void rejectsDisabledTemplateAsNotFound() {
            when(templateMapper.selectById(3)).thenReturn(template(OWNER, "0"));

            assertThatThrownBy(() -> config.templateOwnershipChecker(templateMapper).check(3, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("模板不存在");
        }

        @Test
        void rejectsOtherUsersTemplateAsUnauthorized() {
            when(templateMapper.selectById(3)).thenReturn(template(OWNER, TemplateBO.STATE_ENABLED));

            assertThatThrownBy(() -> config.templateOwnershipChecker(templateMapper).check(3, OTHER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("模板不归属当前用户");
        }

        private TemplateDO template(Integer userId, String state) {
            TemplateDO template = new TemplateDO();
            template.setTemplateId(3);
            template.setUserId(userId);
            template.setState(state);
            return template;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class DeviceChecker {

        @Mock
        private DeviceService deviceService;

        @Test
        void resourceNameIsDevice() {
            assertThat(config.deviceOwnershipChecker(deviceService).getResource()).isEqualTo("device");
        }

        @Test
        void passesWhenOwnedByUser() {
            when(deviceService.getBO("dev-1")).thenReturn(device(OWNER));

            assertThatCode(() -> config.deviceOwnershipChecker(deviceService).check(" dev-1 ", OWNER))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsMissingDeviceAsNotFound() {
            when(deviceService.getBO("dev-1")).thenReturn(null);

            assertThatThrownBy(() -> config.deviceOwnershipChecker(deviceService).check("dev-1", OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("设备不存在");
        }

        @Test
        void rejectsOtherUsersDeviceAsUnauthorized() {
            when(deviceService.getBO("dev-1")).thenReturn(device(OWNER));

            assertThatThrownBy(() -> config.deviceOwnershipChecker(deviceService).check("dev-1", OTHER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("设备不归属当前用户");
        }

        /** 未绑定用户的设备不能被任何登录用户操作。 */
        @Test
        void rejectsUnboundDeviceAsUnauthorized() {
            when(deviceService.getBO("dev-1")).thenReturn(device(null));

            assertThatThrownBy(() -> config.deviceOwnershipChecker(deviceService).check("dev-1", OWNER))
                .isInstanceOf(UnauthorizedException.class);
        }

        private DeviceBO device(Integer userId) {
            DeviceBO device = new DeviceBO();
            device.setDeviceId("dev-1");
            device.setUserId(userId);
            return device;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class MessageChecker {

        @Mock
        private MessageService messageService;

        @Test
        void resourceNameIsMessage() {
            assertThat(config.messageOwnershipChecker(messageService).getResource()).isEqualTo("message");
        }

        @Test
        void passesWhenOwnedByUser() {
            when(messageService.getBO(3)).thenReturn(message(OWNER));

            assertThatCode(() -> config.messageOwnershipChecker(messageService).check(3, OWNER))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsMissingMessageAsNotFound() {
            when(messageService.getBO(3)).thenReturn(null);

            assertThatThrownBy(() -> config.messageOwnershipChecker(messageService).check(3, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("消息不存在");
        }

        @Test
        void rejectsOtherUsersMessageAsUnauthorized() {
            when(messageService.getBO(3)).thenReturn(message(OWNER));

            assertThatThrownBy(() -> config.messageOwnershipChecker(messageService).check(3, OTHER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("消息不归属当前用户");
        }

        private MessageBO message(Integer userId) {
            MessageBO message = new MessageBO();
            message.setMessageId(3L);
            message.setUserId(userId);
            return message;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class UserChecker {

        @Mock
        private UserService userService;

        @Test
        void resourceNameIsUser() {
            assertThat(config.userOwnershipChecker(userService).getResource()).isEqualTo("user");
        }

        @Test
        void passesWhenTargetIsSelf() {
            when(userService.getBO(OWNER)).thenReturn(user(OWNER));

            assertThatCode(() -> config.userOwnershipChecker(userService).check(OWNER, OWNER))
                .doesNotThrowAnyException();
        }

        @Test
        void rejectsMissingUserAsNotFound() {
            when(userService.getBO(9)).thenReturn(null);

            assertThatThrownBy(() -> config.userOwnershipChecker(userService).check(9, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户不存在");
        }

        @Test
        void rejectsAnotherUserAsUnauthorized() {
            when(userService.getBO(OTHER)).thenReturn(user(OTHER));

            assertThatThrownBy(() -> config.userOwnershipChecker(userService).check(OTHER, OWNER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("用户不归属当前登录人");
        }

        private UserBO user(Integer userId) {
            UserBO user = new UserBO();
            user.setUserId(userId);
            return user;
        }
    }
}
