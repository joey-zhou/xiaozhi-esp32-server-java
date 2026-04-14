package com.xiaozhi.device.domain.repository;

import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.vo.VerifyCode;

import java.util.Optional;
import java.util.Set;

/**
 * Device 聚合根仓储接口（领域层定义，基础设施层实现）。
 */
public interface DeviceRepository {

    /** 按设备 ID 加载聚合根 */
    Optional<Device> findById(String deviceId);

    /** 按验证码查询（设备激活场景） */
    Optional<VerifyCode> findVerifyCode(String code, String deviceId, String sessionId);

    /**
     * 持久化聚合根（新建或更新）。
     * <p>实现类需在保存完成后调用 {@link Device#pullSignals()} 并发布对应 ApplicationEvent。
     */
    void save(Device device);

    /** 删除设备并清除缓存 */
    void delete(String deviceId);

    /**
     * 直接更新设备状态（热路径）。
     * <p>不加载完整聚合根，直接执行 UPDATE + 缓存失效，无领域事件。
     */
    void updateState(String deviceId, String state);

    /**
     * 批量重置设备状态（热路径，如实例重启时批量离线）。
     */
    int batchUpdateState(Set<String> deviceIds, String state);
}
