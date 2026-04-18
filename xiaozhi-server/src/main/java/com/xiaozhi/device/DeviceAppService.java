package com.xiaozhi.device;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.req.DeviceBatchUpdateReq;
import com.xiaozhi.common.model.req.DeviceCreateReq;
import com.xiaozhi.common.model.req.DevicePageReq;
import com.xiaozhi.common.model.req.DeviceUpdateReq;
import com.xiaozhi.common.model.req.OtaReq;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.communication.registry.DialogueServerInfo;
import com.xiaozhi.communication.registry.DialogueServerRegistry;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.domain.vo.VerifyCode;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.CommonUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
/**
 * 设备领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>跨领域校验（角色归属验证）</li>
 *   <li>副作用协调（Redis 广播设备会话变更、角色切换）</li>
 * </ul>
 */
@Slf4j
@Service
public class DeviceAppService {

    @Resource
    private DeviceService deviceService;

    @Resource
    private DeviceRepository deviceRepository;

    @Resource
    private DeviceConvert deviceConvert;

    @Resource
    private RoleService roleService;

    @Resource
    private ServerAddressProvider serverAddressProvider;

    @Resource
    private DialogueServerRegistry dialogueServerRegistry;


    public PageResp<DeviceResp> page(DevicePageReq req, Integer userId) {
        DevicePageReq r = req == null ? new DevicePageReq() : req;
        return deviceService.page(r.getPageNo(), r.getPageSize(),
            r.getDeviceId(), r.getDeviceName(), r.getRoleName(),
            r.getState(), r.getRoleId(), userId);
    }

    @Transactional
    public DeviceResp create(DeviceCreateReq req, Integer userId) {
        VerifyCode verifyCode = deviceRepository.findVerifyCode(req.getCode(), null, null)
                .orElseThrow(() -> new IllegalArgumentException("无效验证码"));

        if (!StringUtils.hasText(verifyCode.deviceId())) {
            throw new IllegalArgumentException("无效验证码");
        }

        // 设备已存在：幂等返回（同一用户）或抛出冲突
        java.util.Optional<Device> existingDevice = deviceRepository.findById(verifyCode.deviceId());
        if (existingDevice.isPresent()) {
            Device d = existingDevice.get();
            if (userId != null && userId.equals(d.getUserId())) {
                DeviceResp result = deviceService.get(d.getDeviceId());
                if (result == null) throw new IllegalStateException("查询设备失败");
                return result;
            }
            throw new IllegalStateException("设备已被其他用户绑定");
        }

        RoleBO selectedRole = roleService.getDefaultOrFirstBO(userId);
        if (selectedRole == null) {
            throw new IllegalStateException("没有配置角色");
        }

        String name = StringUtils.hasText(verifyCode.type()) ? verifyCode.type() : "小智";
        Device device = Device.newDevice(verifyCode.deviceId(), name, verifyCode.type(),
                userId, selectedRole.getRoleId());
        deviceRepository.save(device);

        DeviceResp result = deviceService.get(device.getDeviceId());
        if (result == null) throw new IllegalStateException("添加设备失败");
        return result;
    }

    @Transactional
    public DeviceResp update(String deviceId, DeviceUpdateReq req) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在或无权访问"));

        if (req.getRoleId() != null) {
            RoleBO role = roleService.getBO(req.getRoleId());
            if (role == null) throw new IllegalArgumentException("角色不存在或无权访问");
            if (!Objects.equals(role.getUserId(), device.getUserId()))
                throw new IllegalArgumentException("角色不属于设备所属用户");
        }

        device.update(req.getDeviceName(), req.getRoleId(), req.getLocation());
        deviceRepository.save(device);

        DeviceResp result = deviceService.get(deviceId);
        if (result == null) throw new IllegalStateException("更新设备失败");
        return result;
    }

    @Transactional
    public Map<String, Object> batchUpdate(DeviceBatchUpdateReq req) {
        if (!StringUtils.hasText(req.getDeviceIds()) || req.getRoleId() == null) {
            throw new IllegalArgumentException("更新失败，请检查设备ID是否正确");
        }
        if (roleService.getBO(req.getRoleId()) == null) {
            throw new IllegalArgumentException("角色不存在或无权访问");
        }

        int successCount = 0;
        for (String rawDeviceId : Arrays.asList(req.getDeviceIds().split(","))) {
            String deviceId = rawDeviceId.trim();
            if (!StringUtils.hasText(deviceId)) {
                continue;
            }
            deviceRepository.findById(deviceId).ifPresent(device -> {
                device.bindRole(req.getRoleId());
                deviceRepository.save(device);
            });
            successCount++;
        }
        if (successCount <= 0) {
            throw new IllegalArgumentException("更新失败，请检查设备ID是否正确");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("successCount", successCount);
        data.put("totalCount", req.getDeviceIds().split(",").length);
        return data;
    }

    public DeviceResp getResp(String deviceId) {
        return deviceService.get(deviceId);
    }

    public DeviceResp generateCode(String deviceId, String sessionId, String type) {
        VerifyCodeBO codeBO = deviceService.generateCode(deviceId, sessionId, type);
        return codeBO == null ? null : deviceConvert.toResp(codeBO);
    }

    public int sync(DeviceBO syncData) {
        if (syncData == null || !StringUtils.hasText(syncData.getDeviceId())) {
            return 0;
        }
        return deviceRepository.findById(syncData.getDeviceId()).map(device -> {
            device.sync(syncData.getDeviceName(), syncData.getWifiName(),
                    syncData.getChipModelName(), syncData.getType(),
                    syncData.getVersion(), syncData.getIp(), syncData.getLocation());
            deviceRepository.save(device);
            return 1;
        }).orElse(0);
    }

    @Transactional
    public void delete(String deviceId) {
        if (deviceRepository.findById(deviceId).isEmpty()) {
            throw new ResourceNotFoundException("设备不存在或无权访问");
        }
        deviceRepository.delete(deviceId);
    }

    /**
     * 处理 OTA 请求的核心业务逻辑。
     *
     * @param req 由 Controller 从 HTTP 请求解析出的设备信息
     * @return OTA 响应数据（firmware / activation / websocket 等）
     * @throws IllegalArgumentException 设备ID不正确
     * @throws IllegalStateException    生成验证码失败等内部错误
     */
    public Map<String, Object> handleOta(OtaReq req) {
        // --- IP 地理位置解析 ---
        if (StringUtils.hasText(req.getIp())) {
            var ipInfo = CmsUtils.getIPInfoByAddress(req.getIp());
            if (ipInfo != null && StringUtils.hasText(ipInfo.getLocation())) {
                req.setLocation(ipInfo.getLocation());
            }
        }

        if (!StringUtils.hasText(req.getDeviceId()) || !CommonUtils.isMacAddressValid(req.getDeviceId())) {
            throw new IllegalArgumentException("设备ID不正确");
        }

        String deviceId = req.getDeviceId();
        DeviceResp boundDevice = getResp(deviceId);
        Map<String, Object> otaResponse = new HashMap<>();

        // --- 固件信息 ---
        Map<String, Object> firmwareInfo = new HashMap<>();
        firmwareInfo.put("url", serverAddressProvider.getOtaAddress());
        firmwareInfo.put("version", "1.0.0");
        otaResponse.put("firmware", firmwareInfo);
        otaResponse.put("server_time", Map.of(
            "timestamp", System.currentTimeMillis(),
            "timezone_offset", 480
        ));

        if (boundDevice == null) {
            // --- 未绑定设备：生成验证码 ---
            DeviceResp codeResult = generateCode(deviceId, null, req.getType());
            if (codeResult == null || !StringUtils.hasText(codeResult.getCode())) {
                throw new IllegalStateException("生成验证码失败");
            }
            otaResponse.put("activation", Map.of(
                "code", codeResult.getCode(),
                "message", codeResult.getCode(),
                "challenge", deviceId
            ));
        } else {
            // --- 已绑定设备：返回通信地址 ---
            DialogueServerInfo selectedServer = null;
            try {
                selectedServer = dialogueServerRegistry.selectServer();
            } catch (RuntimeException e) {
                log.warn("选择对话服务器失败，回退默认地址, deviceId={}", deviceId, e);
            }
            String websocketAddress = selectedServer != null ? selectedServer.getWebsocketAddress() : serverAddressProvider.getWebsocketAddress();

            Map<String, Object> websocketData = new HashMap<>();
            websocketData.put("url", websocketAddress);
            websocketData.put("token", "");
            otaResponse.put("websocket", websocketData);

            // --- 同步设备信息 ---
            DeviceBO syncData = new DeviceBO();
            syncData.setDeviceId(boundDevice.getDeviceId());
            syncData.setDeviceName(boundDevice.getDeviceName());
            syncData.setIp(req.getIp());
            syncData.setLocation(req.getLocation());
            syncData.setWifiName(req.getWifiName());
            syncData.setChipModelName(req.getChipModelName());
            syncData.setType(req.getType());
            syncData.setVersion(req.getVersion());
            try {
                sync(syncData);
            } catch (RuntimeException e) {
                log.warn("同步设备信息失败，不影响OTA返回, deviceId={}", deviceId, e);
            }
        }

        return otaResponse;
    }

    /**
     * 检查 OTA 激活状态。
     *
     * @return true 表示设备已激活，false 表示未激活或设备ID无效
     */
    public boolean checkOtaActivation(String deviceId) {
        if (!StringUtils.hasText(deviceId) || !CommonUtils.isMacAddressValid(deviceId)) {
            return false;
        }
        DeviceResp device = getResp(deviceId);
        if (device == null) {
            return false;
        }
        log.info("OTA激活结果查询成功, deviceId: {} 激活时间: {}", deviceId, device.getCreateTime());
        return true;
    }
}
