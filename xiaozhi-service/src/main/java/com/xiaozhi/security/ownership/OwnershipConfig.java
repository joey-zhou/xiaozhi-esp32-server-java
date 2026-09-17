package com.xiaozhi.security.ownership;

import com.xiaozhi.common.exception.ResourceNotFoundException;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
@Configuration
public class OwnershipConfig {

    @Bean
    public OwnershipChecker roleOwnershipChecker(RoleService roleService) {
        return new AbstractOwnershipChecker("role") {
            @Override
            public void check(Object resourceId, Integer userId) {
                RoleBO role = roleService.getBO(toIntId(resourceId, "roleId"));
                if (role == null) {
                    throw new ResourceNotFoundException("角色不存在");
                }
                requireOwner(role.getUserId(), userId, "角色不归属当前用户");
            }
        };
    }

    /** 引用路径（把某个模型/音色绑到自己的角色上）用的检查器。 */
    @Bean
    public OwnershipChecker configOwnershipChecker(ConfigService configService) {
        return new AbstractOwnershipChecker("config") {
            @Override
            public void check(Object resourceId, Integer userId) {
                ConfigBO config = requireConfig(configService, toIntId(resourceId, "configId"));
                requireOwner(config.getUserId(), userId, "配置不归属当前用户");
            }
        };
    }

    /** 写路径（改配置、删配置）用的检查器。 */
    @Bean
    public OwnershipChecker configWriteOwnershipChecker(ConfigService configService) {
        return new AbstractOwnershipChecker("configWrite") {
            @Override
            public void check(Object resourceId, Integer userId) {
                ConfigBO config = requireConfig(configService, toIntId(resourceId, "configId"));
                requireOwner(config.getUserId(), userId, "配置不归属当前用户");
            }
        };
    }

    /** 两个 config 检查器共用的取数。 */
    private static ConfigBO requireConfig(ConfigService configService, Integer configId) {
        ConfigBO config = configService.getBO(configId);
        if (config == null) {
            throw new ResourceNotFoundException("配置不存在");
        }
        return config;
    }

    @Bean
    public OwnershipChecker templateOwnershipChecker(TemplateService templateService) {
        return new AbstractOwnershipChecker("template") {
            @Override
            public void check(Object resourceId, Integer userId) {
                TemplateBO template = templateService.getBO(toIntId(resourceId, "templateId"));
                if (template == null) {
                    throw new ResourceNotFoundException("模板不存在");
                }
                requireOwner(template.getUserId(), userId, "模板不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker deviceOwnershipChecker(DeviceService deviceService) {
        return new AbstractOwnershipChecker("device") {
            @Override
            public void check(Object resourceId, Integer userId) {
                DeviceBO device = deviceService.getBO(toStrId(resourceId, "deviceId"));
                if (device == null) {
                    throw new ResourceNotFoundException("设备不存在");
                }
                requireOwner(device.getUserId(), userId, "设备不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker messageOwnershipChecker(MessageService messageService) {
        return new AbstractOwnershipChecker("message") {
            @Override
            public void check(Object resourceId, Integer userId) {
                MessageBO message = messageService.getBO(toLongId(resourceId, "messageId"));
                if (message == null) {
                    throw new ResourceNotFoundException("消息不存在");
                }
                requireOwner(message.getUserId(), userId, "消息不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker userOwnershipChecker(UserService userService) {
        return new AbstractOwnershipChecker("user") {
            @Override
            public void check(Object resourceId, Integer userId) {
                UserBO user = userService.getBO(toIntId(resourceId, "userId"));
                if (user == null) {
                    throw new ResourceNotFoundException("用户不存在");
                }
                requireOwner(user.getUserId(), userId, "用户不归属当前登录人");
            }
        };
    }
}
