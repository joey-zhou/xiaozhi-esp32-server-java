package com.xiaozhi.communication.protocol;

import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.domain.vo.VerifyCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 不落库的设备仓储假体，全部状态在内存里。
 *
 * <p>协议链路上的设备状态写库都在虚拟线程里跑，断言前必须用
 * {@link AwaitHelper#until} 等到流水出现，不能写完立刻断言。
 *
 * <p>盲区：不模拟事务、缓存失效与领域事件发布，{@code save} 也不会触发
 * {@code Device#pullSignals()} 对应的 ApplicationEvent。
 */
class RecordingDeviceRepository implements DeviceRepository {

    /** 一次设备状态写入的记录 */
    record StateUpdate(String deviceId, String state) {
    }

    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final List<StateUpdate> stateUpdates = new CopyOnWriteArrayList<>();
    private final List<Device> saved = new CopyOnWriteArrayList<>();
    private final List<String> deleted = new CopyOnWriteArrayList<>();
    private final List<String> invalidatedCodes = new CopyOnWriteArrayList<>();

    @Override
    public Optional<Device> findById(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    @Override
    public Optional<VerifyCode> findVerifyCode(String code, String deviceId, String sessionId) {
        return Optional.empty();
    }

    @Override
    public void invalidateVerifyCodes(String deviceId) {
        invalidatedCodes.add(deviceId);
    }

    @Override
    public void save(Device device) {
        saved.add(device);
        if (device.getDeviceId() != null) {
            devices.put(device.getDeviceId(), device);
        }
    }

    @Override
    public void delete(String deviceId) {
        deleted.add(deviceId);
        devices.remove(deviceId);
    }

    @Override
    public void updateState(String deviceId, String state) {
        stateUpdates.add(new StateUpdate(deviceId, state));
    }

    @Override
    public int batchUpdateState(Set<String> deviceIds, String state) {
        deviceIds.forEach(id -> stateUpdates.add(new StateUpdate(id, state)));
        return deviceIds.size();
    }

    // ========== 断言入口 ==========

    List<StateUpdate> stateUpdates() {
        return List.copyOf(stateUpdates);
    }

    List<Device> savedDevices() {
        return List.copyOf(saved);
    }

    List<String> deletedDeviceIds() {
        return List.copyOf(deleted);
    }

    List<String> invalidatedVerifyCodeDeviceIds() {
        return List.copyOf(invalidatedCodes);
    }
}
