package com.xiaozhi.device.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Device 聚合根。
 * <p>
 * 职责：持有设备状态，通过行为方法（非 setter）修改状态并收集领域信号。
 * 无 public setter，状态变更必须通过行为方法。
 */
@Getter
public class Device {

    /** 领域信号：由 Repository.save() 转译为 Spring ApplicationEvent 发布 */
    public enum DomainSignal { UPDATED, ONLINE, ROLE_CHANGED, SESSION_CLOSED }

    /** 持久化设备状态常量 */
    public static final String STATE_OFFLINE = "0";
    public static final String STATE_ONLINE  = "1";
    public static final String STATE_STANDBY = "2";

    // --- Identity ---
    private final String deviceId;

    // --- Core state ---
    private String deviceName;
    private Integer userId;
    private Integer roleId;
    private String mcpList;

    // --- Network / hardware info ---
    private String ip;
    private String location;
    private String wifiName;
    private String chipModelName;
    private String type;
    private String version;
    private String state;

    // --- Timestamps (read-only after creation) ---
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    private final List<DomainSignal> signals = new ArrayList<>();

    /** 从持久层重建聚合根（Repository 专用） */
    public Device(String deviceId, String deviceName, Integer userId, Integer roleId,
           String mcpList, String ip, String location, String wifiName,
           String chipModelName, String type, String version, String state,
           LocalDateTime createTime, LocalDateTime updateTime) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.userId = userId;
        this.roleId = roleId;
        this.mcpList = mcpList;
        this.ip = ip;
        this.location = location;
        this.wifiName = wifiName;
        this.chipModelName = chipModelName;
        this.type = type;
        this.version = version;
        this.state = state;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /** 工厂方法：创建全新设备（首次激活） */
    public static Device newDevice(String deviceId, String deviceName, String type,
                                   Integer userId, Integer roleId) {
        Device device = new Device(
                deviceId,
                deviceName != null && !deviceName.isBlank() ? deviceName : (type != null ? type : "小智"),
                userId, roleId, null, null, null, null, null, type,
                null, STATE_OFFLINE, null, null);
        device.signals.add(DomainSignal.UPDATED);
        device.signals.add(DomainSignal.SESSION_CLOSED);
        return device;
    }

    // ===================== 行为方法 =====================

    /** 绑定用户（设备激活），同时指定默认角色 */
    public void bindUser(Integer userId, Integer roleId) {
        if (this.userId != null && !this.userId.equals(userId)) {
            throw new IllegalStateException("设备已被其他用户绑定");
        }
        this.userId = userId;
        this.roleId = roleId;
        signals.add(DomainSignal.UPDATED);
    }

    /** 切换关联角色 */
    public void bindRole(Integer roleId) {
        if (!Objects.equals(this.roleId, roleId)) {
            this.roleId = roleId;
            signals.add(DomainSignal.UPDATED);
            signals.add(DomainSignal.ROLE_CHANGED);
        }
    }

    /** 更新可编辑字段（来自 DeviceUpdateReq） */
    public void update(String deviceName, Integer roleId, String location) {
        if (deviceName != null && !deviceName.isBlank()) this.deviceName = deviceName;
        if (roleId != null && !Objects.equals(this.roleId, roleId)) {
            this.roleId = roleId;
            signals.add(DomainSignal.ROLE_CHANGED);
        } else if (roleId != null) {
            this.roleId = roleId;
        }
        if (location != null && !location.isBlank()) this.location = location;
        signals.add(DomainSignal.UPDATED);
    }

    /** 设备上线时同步网络信息 */
    public void reportOnline(String ip, String version, String wifiName, String location) {
        if (ip != null && !ip.isBlank()) this.ip = ip;
        if (version != null && !version.isBlank()) this.version = version;
        if (wifiName != null && !wifiName.isBlank()) this.wifiName = wifiName;
        if (location != null && !location.isBlank()) this.location = location;
        signals.add(DomainSignal.ONLINE);
    }

    /** 更新 MCP 工具列表（设备连接时上报） */
    public void updateMcpList(String mcpList) {
        this.mcpList = mcpList;
        signals.add(DomainSignal.UPDATED);
    }

    /** OTA 上报时的部分字段同步 */
    public void sync(String deviceName, String wifiName, String chipModelName,
                     String type, String version, String ip, String location) {
        if (deviceName != null && !deviceName.isBlank()) this.deviceName = deviceName;
        if (wifiName != null && !wifiName.isBlank()) this.wifiName = wifiName;
        if (chipModelName != null && !chipModelName.isBlank()) this.chipModelName = chipModelName;
        if (type != null && !type.isBlank()) this.type = type;
        if (version != null && !version.isBlank()) this.version = version;
        if (ip != null && !ip.isBlank()) this.ip = ip;
        if (location != null && !location.isBlank()) this.location = location;
        signals.add(DomainSignal.UPDATED);
    }

    /**
     * 提取并清空已收集的领域信号。
     * <p>由 Repository.save() 在持久化完成后调用，转译为 Spring ApplicationEvent。
     */
    public List<DomainSignal> pullSignals() {
        List<DomainSignal> result = List.copyOf(signals);
        signals.clear();
        return result;
    }
}
