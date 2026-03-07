package com.xiaozhi.utils;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.dto.response.*;
import com.xiaozhi.entity.*;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DTO转换工具类
 * 用于Entity和DTO之间的转换
 *
 * @author Joey
 */
public class DtoConverter {

    /**
     * 通用分页转换方法
     * 将 PageHelper 分页后的 List 转换为 DTO 的 PageInfo，保留完整分页信息
     *
     * @param sourceList 原始分页列表（由 PageHelper 分页后的结果）
     * @param converter  列表转换函数
     * @param <S>        源类型
     * @param <T>        目标类型
     * @return 转换后的 PageInfo
     */
    public static <S, T> PageInfo<T> toPageInfo(List<S> sourceList, Function<List<S>, List<T>> converter) {
        // 先获取原始分页信息（PageHelper 的分页信息在此时有效）
        PageInfo<S> originalPageInfo = new PageInfo<>(sourceList);
        // 转换列表
        List<T> targetList = converter.apply(sourceList);
        // 复制分页信息并替换列表
        PageInfo<T> resultPageInfo = new PageInfo<>();
        BeanUtils.copyProperties(originalPageInfo, resultPageInfo);
        resultPageInfo.setList(targetList);
        return resultPageInfo;
    }

    /**
     * SysUser -> UserDTO
     * 排除敏感信息(password, wxOpenId等)
     */
    public static UserDTO toUserDTO(SysUser user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

    /**
     * SysDevice -> DeviceDTO
     */
    public static DeviceDTO toDeviceDTO(SysDevice device) {
        if (device == null) {
            return null;
        }
        DeviceDTO dto = new DeviceDTO();
        BeanUtils.copyProperties(device, dto);
        return dto;
    }

    /**
     * SysAuthRole -> RoleDTO
     */
    public static RoleDTO toRoleDTO(SysAuthRole role) {
        if (role == null) {
            return null;
        }
        RoleDTO dto = new RoleDTO();
        BeanUtils.copyProperties(role, dto);
        return dto;
    }

    /**
     * SysPermission -> PermissionDTO
     */
    public static PermissionDTO toPermissionDTO(SysPermission permission) {
        if (permission == null) {
            return null;
        }
        PermissionDTO dto = new PermissionDTO();
        BeanUtils.copyProperties(permission, dto);

        // 递归转换子权限
        if (permission.getChildren() != null && !permission.getChildren().isEmpty()) {
            dto.setChildren(
                permission.getChildren().stream()
                    .map(DtoConverter::toPermissionDTO)
                    .collect(Collectors.toList())
            );
        }

        return dto;
    }

    /**
     * List<SysPermission> -> List<PermissionDTO>
     */
    public static List<PermissionDTO> toPermissionDTOList(List<SysPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>();
        }
        return permissions.stream()
            .map(DtoConverter::toPermissionDTO)
            .collect(Collectors.toList());
    }

    /**
     * List<SysUser> -> List<UserDTO>
     */
    public static List<UserDTO> toUserDTOList(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        return users.stream()
            .map(DtoConverter::toUserDTO)
            .collect(Collectors.toList());
    }

    /**
     * List<SysDevice> -> List<DeviceDTO>
     */
    public static List<DeviceDTO> toDeviceDTOList(List<SysDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return new ArrayList<>();
        }
        return devices.stream()
            .map(DtoConverter::toDeviceDTO)
            .collect(Collectors.toList());
    }

    /**
     * SysAgent -> AgentDTO
     */
    public static AgentDTO toAgentDTO(SysAgent agent) {
        if (agent == null) {
            return null;
        }
        AgentDTO dto = new AgentDTO();
        BeanUtils.copyProperties(agent, dto);
        // 注意: apiKey, apiSecret, ak, sk 等敏感字段不会被复制到DTO中(DTO中没有这些字段)
        return dto;
    }

    /**
     * List<SysAgent> -> List<AgentDTO>
     */
    public static List<AgentDTO> toAgentDTOList(List<SysAgent> agentList) {
        if (agentList == null || agentList.isEmpty()) {
            return new ArrayList<>();
        }
        return agentList.stream()
            .map(DtoConverter::toAgentDTO)
            .collect(Collectors.toList());
    }

    /**
     * SysMessage -> MessageDTO
     */
    public static MessageDTO toMessageDTO(SysMessage message) {
        if (message == null) {
            return null;
        }
        MessageDTO dto = new MessageDTO();
        BeanUtils.copyProperties(message, dto);
        return dto;
    }

    /**
     * List<SysMessage> -> List<MessageDTO>
     */
    public static List<MessageDTO> toMessageDTOList(List<SysMessage> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return new ArrayList<>();
        }
        return messageList.stream()
            .map(DtoConverter::toMessageDTO)
            .collect(Collectors.toList());
    }

    /**
     * SysConfig -> ConfigDTO
     */
    public static ConfigDTO toConfigDTO(SysConfig config) {
        if (config == null) {
            return null;
        }
        ConfigDTO dto = new ConfigDTO();
        BeanUtils.copyProperties(config, dto);
        // 注意: apiKey, apiSecret, ak, sk 等敏感字段不会被复制到DTO中(DTO中没有这些字段)
        return dto;
    }

    /**
     * List<SysConfig> -> List<ConfigDTO>
     */
    public static List<ConfigDTO> toConfigDTOList(List<SysConfig> configList) {
        if (configList == null || configList.isEmpty()) {
            return new ArrayList<>();
        }
        return configList.stream()
            .map(DtoConverter::toConfigDTO)
            .collect(Collectors.toList());
    }

    /**
     * SysRole -> RoleDTO
     */
    public static RoleDTO toRoleDTO(SysRole role) {
        if (role == null) {
            return null;
        }
        RoleDTO dto = new RoleDTO();
        BeanUtils.copyProperties(role, dto);
        return dto;
    }

    /**
     * List<SysRole> -> List<RoleDTO>
     */
    public static List<RoleDTO> toRoleDTOList(List<SysRole> roleList) {
        if (roleList == null || roleList.isEmpty()) {
            return new ArrayList<>();
        }
        return roleList.stream()
            .map(DtoConverter::toRoleDTO)
            .collect(Collectors.toList());
    }

    /**
     * SysTemplate -> TemplateDTO
     */
    public static TemplateDTO toTemplateDTO(SysTemplate template) {
        if (template == null) {
            return null;
        }
        TemplateDTO dto = new TemplateDTO();
        BeanUtils.copyProperties(template, dto);
        return dto;
    }

    /**
     * List<SysTemplate> -> List<TemplateDTO>
     */
    public static List<TemplateDTO> toTemplateDTOList(List<SysTemplate> templateList) {
        if (templateList == null || templateList.isEmpty()) {
            return new ArrayList<>();
        }
        return templateList.stream()
            .map(DtoConverter::toTemplateDTO)
            .collect(Collectors.toList());
    }

}
