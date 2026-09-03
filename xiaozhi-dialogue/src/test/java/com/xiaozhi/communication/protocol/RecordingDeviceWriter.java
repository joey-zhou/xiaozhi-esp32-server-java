package com.xiaozhi.communication.protocol;

import com.xiaozhi.common.port.DeviceWriter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 写库在虚拟线程里跑，断言前须用 {@link AwaitHelper#until} 等待。
 */
class RecordingDeviceWriter implements DeviceWriter {

    record StateUpdate(String deviceId, String state) {
    }

    record Registration(String deviceId, String deviceName, String type, Integer userId, Integer roleId) {
    }

    record RoleBinding(String deviceId, Integer roleId) {
    }

    record McpListUpdate(String deviceId, String mcpList) {
    }

    private final List<StateUpdate> stateUpdates = new CopyOnWriteArrayList<>();
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    private final List<RoleBinding> roleBindings = new CopyOnWriteArrayList<>();
    private final List<McpListUpdate> mcpListUpdates = new CopyOnWriteArrayList<>();

    @Override
    public void updateState(String deviceId, String state) {
        stateUpdates.add(new StateUpdate(deviceId, state));
    }

    @Override
    public int batchUpdateState(Set<String> deviceIds, String state) {
        deviceIds.forEach(id -> stateUpdates.add(new StateUpdate(id, state)));
        return deviceIds.size();
    }

    @Override
    public void updateMcpList(String deviceId, String mcpList) {
        mcpListUpdates.add(new McpListUpdate(deviceId, mcpList));
    }

    @Override
    public void bindRole(String deviceId, Integer roleId) {
        roleBindings.add(new RoleBinding(deviceId, roleId));
    }

    @Override
    public void register(String deviceId, String deviceName, String type, Integer userId, Integer roleId) {
        registrations.add(new Registration(deviceId, deviceName, type, userId, roleId));
    }

    List<StateUpdate> stateUpdates() {
        return List.copyOf(stateUpdates);
    }

    List<Registration> registrations() {
        return List.copyOf(registrations);
    }

    List<RoleBinding> roleBindings() {
        return List.copyOf(roleBindings);
    }

    List<McpListUpdate> mcpListUpdates() {
        return List.copyOf(mcpListUpdates);
    }
}
