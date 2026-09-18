package com.xiaozhi.security.ownership;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.TemplateBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.template.service.TemplateService;
import com.xiaozhi.user.service.UserService;
import org.junit.jupiter.api.Test;

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
            config.roleOwnershipChecker(mock(RoleService.class)).getResource(),
            config.configOwnershipChecker(mock(ConfigService.class)).getResource(),
            config.configWriteOwnershipChecker(mock(ConfigService.class)).getResource(),
            config.templateOwnershipChecker(mock(TemplateService.class)).getResource(),
            config.deviceOwnershipChecker(mock(DeviceService.class)).getResource(),
            config.messageOwnershipChecker(mock(MessageService.class)).getResource(),
            config.userOwnershipChecker(mock(UserService.class)).getResource());

        assertThat(resources).doesNotHaveDuplicates()
            .containsExactly("role", "config", "configWrite", "template", "device", "message", "user");
    }

    @Test
    void roleCheckerIsNamedRole() {
        assertThat(config.roleOwnershipChecker(mock(RoleService.class)).getResource()).isEqualTo("role");
    }

    @Test
    void roleCheckPassesWhenOwnedByUser() {
        RoleService roleService = mock(RoleService.class);
        when(roleService.getBO(3)).thenReturn(role(OWNER));

        assertThatCode(() -> config.roleOwnershipChecker(roleService).check(3, OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void roleCheckRejectsMissingRoleAsNotFound() {
        RoleService roleService = mock(RoleService.class);
        when(roleService.getBO(3)).thenReturn(null);

        assertThatThrownBy(() -> config.roleOwnershipChecker(roleService).check("3", OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("角色不存在");
    }

    @Test
    void roleCheckRejectsOtherUsersRoleAsUnauthorized() {
        RoleService roleService = mock(RoleService.class);
        when(roleService.getBO(3)).thenReturn(role(OWNER));

        assertThatThrownBy(() -> config.roleOwnershipChecker(roleService).check(3, OTHER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("角色不归属当前用户");
    }

    /** 归属列为 null 的历史数据不能被当作「谁都能改」。 */
    @Test
    void roleCheckRejectsRoleWithoutOwnerAsUnauthorized() {
        RoleService roleService = mock(RoleService.class);
        when(roleService.getBO(3)).thenReturn(role(null));

        assertThatThrownBy(() -> config.roleOwnershipChecker(roleService).check(3, OWNER))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void configCheckerIsNamedConfig() {
        assertThat(config.configOwnershipChecker(mock(ConfigService.class)).getResource()).isEqualTo("config");
    }

    @Test
    void configCheckPassesWhenOwnedByUser() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBO(3)).thenReturn(configBO(OWNER));

        assertThatCode(() -> config.configOwnershipChecker(configService).check(3, OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void configCheckRejectsMissingConfigAsNotFound() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBO(3)).thenReturn(null);

        assertThatThrownBy(() -> config.configOwnershipChecker(configService).check(3, OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("配置不存在");
    }

    @Test
    void configCheckRejectsOtherUsersConfigAsUnauthorized() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBO(3)).thenReturn(configBO(OWNER));

        assertThatThrownBy(() -> config.configOwnershipChecker(configService).check(3, OTHER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("配置不归属当前用户");
    }

    @Test
    void configWriteCheckerIsNamedConfigWrite() {
        assertThat(config.configWriteOwnershipChecker(mock(ConfigService.class)).getResource())
            .isEqualTo("configWrite");
    }

    /** 写路径（改配置、删配置）用的检查器。 */
    @Test
    void configWriteCheckPassesWhenOwnedByUser() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBO(3)).thenReturn(configBO(OWNER));

        assertThatCode(() -> config.configWriteOwnershipChecker(configService).check(3, OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void configWriteCheckRejectsMissingConfigAsNotFound() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBO(3)).thenReturn(null);

        assertThatThrownBy(() -> config.configWriteOwnershipChecker(configService).check(3, OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("配置不存在");
    }

    @Test
    void configWriteCheckRejectsOtherUsersConfigAsUnauthorized() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBO(3)).thenReturn(configBO(OWNER));

        assertThatThrownBy(() -> config.configWriteOwnershipChecker(configService).check(3, OTHER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("配置不归属当前用户");
    }

    @Test
    void templateCheckerIsNamedTemplate() {
        assertThat(config.templateOwnershipChecker(mock(TemplateService.class)).getResource()).isEqualTo("template");
    }

    @Test
    void templateCheckPassesWhenOwnedByUser() {
        TemplateService templateService = mock(TemplateService.class);
        when(templateService.getBO(3)).thenReturn(template(OWNER));

        assertThatCode(() -> config.templateOwnershipChecker(templateService).check(3, OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void templateCheckRejectsMissingTemplateAsNotFound() {
        TemplateService templateService = mock(TemplateService.class);
        when(templateService.getBO(3)).thenReturn(null);

        assertThatThrownBy(() -> config.templateOwnershipChecker(templateService).check(3, OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("模板不存在");
    }

    /** 已停用的模板与不存在等价，否则删除后仍可被引用；TemplateService#getBO 内部已按 state 过滤，停用模板会直接返回 null。 */
    @Test
    void templateCheckRejectsDisabledTemplateAsNotFound() {
        TemplateService templateService = mock(TemplateService.class);
        when(templateService.getBO(3)).thenReturn(null);

        assertThatThrownBy(() -> config.templateOwnershipChecker(templateService).check(3, OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("模板不存在");
    }

    @Test
    void templateCheckRejectsOtherUsersTemplateAsUnauthorized() {
        TemplateService templateService = mock(TemplateService.class);
        when(templateService.getBO(3)).thenReturn(template(OWNER));

        assertThatThrownBy(() -> config.templateOwnershipChecker(templateService).check(3, OTHER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("模板不归属当前用户");
    }

    @Test
    void deviceCheckerIsNamedDevice() {
        assertThat(config.deviceOwnershipChecker(mock(DeviceService.class)).getResource()).isEqualTo("device");
    }

    @Test
    void deviceCheckPassesWhenOwnedByUser() {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getBO("dev-1")).thenReturn(device(OWNER));

        assertThatCode(() -> config.deviceOwnershipChecker(deviceService).check(" dev-1 ", OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void deviceCheckRejectsMissingDeviceAsNotFound() {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getBO("dev-1")).thenReturn(null);

        assertThatThrownBy(() -> config.deviceOwnershipChecker(deviceService).check("dev-1", OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("设备不存在");
    }

    @Test
    void deviceCheckRejectsOtherUsersDeviceAsUnauthorized() {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getBO("dev-1")).thenReturn(device(OWNER));

        assertThatThrownBy(() -> config.deviceOwnershipChecker(deviceService).check("dev-1", OTHER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("设备不归属当前用户");
    }

    /** 未绑定用户的设备不能被任何登录用户操作。 */
    @Test
    void deviceCheckRejectsUnboundDeviceAsUnauthorized() {
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getBO("dev-1")).thenReturn(device(null));

        assertThatThrownBy(() -> config.deviceOwnershipChecker(deviceService).check("dev-1", OWNER))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void messageCheckerIsNamedMessage() {
        assertThat(config.messageOwnershipChecker(mock(MessageService.class)).getResource()).isEqualTo("message");
    }

    @Test
    void messageCheckPassesWhenOwnedByUser() {
        MessageService messageService = mock(MessageService.class);
        when(messageService.getBO(3L)).thenReturn(message(OWNER));

        assertThatCode(() -> config.messageOwnershipChecker(messageService).check(3L, OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void messageCheckRejectsMissingMessageAsNotFound() {
        MessageService messageService = mock(MessageService.class);
        when(messageService.getBO(3L)).thenReturn(null);

        assertThatThrownBy(() -> config.messageOwnershipChecker(messageService).check(3L, OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("消息不存在");
    }

    @Test
    void messageCheckRejectsOtherUsersMessageAsUnauthorized() {
        MessageService messageService = mock(MessageService.class);
        when(messageService.getBO(3L)).thenReturn(message(OWNER));

        assertThatThrownBy(() -> config.messageOwnershipChecker(messageService).check(3L, OTHER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("消息不归属当前用户");
    }

    @Test
    void userCheckerIsNamedUser() {
        assertThat(config.userOwnershipChecker(mock(UserService.class)).getResource()).isEqualTo("user");
    }

    @Test
    void userCheckPassesWhenTargetIsSelf() {
        UserService userService = mock(UserService.class);
        when(userService.getBO(OWNER)).thenReturn(user(OWNER));

        assertThatCode(() -> config.userOwnershipChecker(userService).check(OWNER, OWNER))
            .doesNotThrowAnyException();
    }

    @Test
    void userCheckRejectsMissingUserAsNotFound() {
        UserService userService = mock(UserService.class);
        when(userService.getBO(9)).thenReturn(null);

        assertThatThrownBy(() -> config.userOwnershipChecker(userService).check(9, OWNER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("用户不存在");
    }

    @Test
    void userCheckRejectsAnotherUserAsUnauthorized() {
        UserService userService = mock(UserService.class);
        when(userService.getBO(OTHER)).thenReturn(user(OTHER));

        assertThatThrownBy(() -> config.userOwnershipChecker(userService).check(OTHER, OWNER))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("用户不归属当前登录人");
    }

    private static RoleBO role(Integer userId) {
        RoleBO role = new RoleBO();
        role.setRoleId(3);
        role.setUserId(userId);
        return role;
    }

    private static ConfigBO configBO(Integer userId) {
        ConfigBO configBO = new ConfigBO();
        configBO.setConfigId(3);
        configBO.setUserId(userId);
        return configBO;
    }

    private static TemplateBO template(Integer userId) {
        TemplateBO template = new TemplateBO();
        template.setTemplateId(3);
        template.setUserId(userId);
        template.setState(TemplateBO.STATE_ENABLED);
        return template;
    }

    private static DeviceBO device(Integer userId) {
        DeviceBO device = new DeviceBO();
        device.setDeviceId("dev-1");
        device.setUserId(userId);
        return device;
    }

    private static MessageBO message(Integer userId) {
        MessageBO message = new MessageBO();
        message.setMessageId(3L);
        message.setUserId(userId);
        return message;
    }

    private static UserBO user(Integer userId) {
        UserBO user = new UserBO();
        user.setUserId(userId);
        return user;
    }
}
