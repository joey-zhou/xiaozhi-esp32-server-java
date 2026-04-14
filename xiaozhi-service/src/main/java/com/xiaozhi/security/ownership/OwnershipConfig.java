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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Objects;
@Configuration
public class OwnershipConfig {

    @Bean
    public OwnershipChecker roleOwnershipChecker(RoleMapper roleMapper) {
        return new AbstractChecker("role") {
            @Override
            public void check(Object resourceId, Integer userId) {
                RoleDO role = roleMapper.selectById(toIntId(resourceId, "roleId"));
                if (role == null) {
                    throw new ResourceNotFoundException("角色不存在");
                }
                requireOwner(role.getUserId(), userId, "角色不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker configOwnershipChecker(ConfigService configService) {
        return new AbstractChecker("config") {
            @Override
            public void check(Object resourceId, Integer userId) {
                ConfigBO config = configService.getBO(toIntId(resourceId, "configId"));
                if (config == null) {
                    throw new ResourceNotFoundException("配置不存在");
                }
                requireOwner(config.getUserId(), userId, "配置不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker templateOwnershipChecker(TemplateMapper templateMapper) {
        return new AbstractChecker("template") {
            @Override
            public void check(Object resourceId, Integer userId) {
                TemplateDO template = templateMapper.selectById(toIntId(resourceId, "templateId"));
                if (template == null || !TemplateBO.STATE_ENABLED.equals(template.getState())) {
                    throw new ResourceNotFoundException("模板不存在");
                }
                requireOwner(template.getUserId(), userId, "模板不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker deviceOwnershipChecker(DeviceService deviceService) {
        return new AbstractChecker("device") {
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
        return new AbstractChecker("message") {
            @Override
            public void check(Object resourceId, Integer userId) {
                MessageBO message = messageService.getBO(toIntId(resourceId, "messageId"));
                if (message == null) {
                    throw new ResourceNotFoundException("消息不存在");
                }
                requireOwner(message.getUserId(), userId, "消息不归属当前用户");
            }
        };
    }

    @Bean
    public OwnershipChecker userOwnershipChecker(UserService userService) {
        return new AbstractChecker("user") {
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

    public abstract static class AbstractChecker implements OwnershipChecker {

        private final String resource;

        public AbstractChecker(String resource) {
            this.resource = resource;
        }

        @Override
        public String getResource() {
            return resource;
        }

        protected final void requireOwner(Integer ownerId, Integer userId, String message) {
            if (!Objects.equals(ownerId, userId)) {
                throw new UnauthorizedException(message);
            }
        }

        protected final Integer toIntId(Object value, String fieldName) {
            if (value instanceof Integer integer) {
                return integer;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                return Integer.valueOf(text.trim());
            }
            throw new IllegalArgumentException(fieldName + " 参数类型不合法");
        }

        protected final Long toLongId(Object value, String fieldName) {
            if (value instanceof Long longValue) {
                return longValue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                return Long.valueOf(text.trim());
            }
            throw new IllegalArgumentException(fieldName + " 参数类型不合法");
        }

        protected final String toStrId(Object value, String fieldName) {
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
            throw new IllegalArgumentException(fieldName + " 参数类型不合法");
        }
    }
}
