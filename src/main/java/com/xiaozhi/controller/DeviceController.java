package com.xiaozhi.controller;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.JsonUtil;

import ch.qos.logback.core.util.StringUtil;
import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 设备管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/device")
@Tag(name = "设备管理", description = "设备相关操作")
public class DeviceController extends BaseController {

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private Environment environment;

    @Resource
    private CmsUtils cmsUtils;

    @Value("${xiaozhi.communication.protocol:both}")
    private String communicationProtocol;

    /**
     * 设备查询
     * 
     * @param device
     * @return deviceList
     */
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询设备", description = "返回设备信息列表")
    public ResultMessage query(SysDevice device, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            device.setUserId(CmsUtils.getUserId());
            List<SysDevice> deviceList = deviceService.query(device, pageFilter);
            ResultMessage result = ResultMessage.success();
            result.put("data", new PageInfo<>(deviceList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加设备
     * 
     * @param code
     */
    @PostMapping("/add")
    @ResponseBody
    @Operation(summary = "添加设备", description = "返回添加结果")
    public ResultMessage add(@Parameter(description = "设备验证码") String code) {
        try {
            SysDevice device = new SysDevice();
            device.setCode(code);
            SysDevice query = deviceService.queryVerifyCode(device);
            if (query == null) {
                return ResultMessage.error("无效验证码");
            }

            device.setUserId(CmsUtils.getUserId());
            device.setDeviceName(query.getType()!= null && !query.getType().isEmpty() ? query.getType() : "小智");
            device.setType(query.getType());
            device.setDeviceId(query.getDeviceId());
            int row = deviceService.add(device);
            if (row > 0) {
                String deviceId = device.getDeviceId();
                ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
                if (session != null) {
                    sessionManager.closeSession(session);
                }

                return ResultMessage.success();
            } else {
                return ResultMessage.error();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("没有配置角色")) {
                return ResultMessage.error(e.getMessage());
            }
            return ResultMessage.error();
        }
    }

    /**
     * 设备信息更新
     * 
     * @param device
     * @return
     */
    @PostMapping("/update")
    @ResponseBody
    @Operation(summary = "更新设备信息", description = "返回更新结果")
    public ResultMessage update(SysDevice device) {
        try {
            device.setUserId(CmsUtils.getUserId());
            deviceService.update(device);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除设备
     * 
     * @param device
     * @return
     */
    @PostMapping("/delete")
    @ResponseBody
    @Operation(summary = "删除设备", description = "返回删除结果")
    public ResultMessage delete(SysDevice device) {
        try {
            device.setUserId(CmsUtils.getUserId());
            // 删除设备
            int rows = deviceService.delete(device);

            if (rows > 0) {
                // 如果设备有会话，清除会话
                String deviceId = device.getDeviceId();
                ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
                if (session != null) {
                    sessionManager.closeSession(session);
                }
                return ResultMessage.success("删除成功");
            } else {
                return ResultMessage.error("删除失败");
            }
        } catch (Exception e) {
            logger.error("删除设备时发生错误", e);
            return ResultMessage.error("删除设备时发生错误");
        }
    }

    @SaIgnore
    @PostMapping("/ota")
    @ResponseBody
    @Operation(summary = "处理OTA请求", description = "返回OTA结果")
    public ResponseEntity<byte[]> ota(
        @Parameter(description = "设备ID") @RequestHeader("Device-Id") String deviceIdAuth,
        @RequestBody String requestBody,
        HttpServletRequest request) {
        try {
            // 读取请求体内容
            SysDevice device = new SysDevice();

            // 解析JSON请求体
            try {
                Map<String, Object> jsonData = JsonUtil.OBJECT_MAPPER.readValue(requestBody, new TypeReference<>() {});

                // 获取设备ID (MAC地址)
                if (deviceIdAuth == null) {
                    if (jsonData.containsKey("mac_address")) {
                        deviceIdAuth = (String) jsonData.get("mac_address");
                    } else if (jsonData.containsKey("mac")) {
                        deviceIdAuth = (String) jsonData.get("mac");
                    }
                }

                // 提取芯片型号
                if (jsonData.containsKey("chip_model_name")) {
                    device.setChipModelName((String) jsonData.get("chip_model_name"));
                    
                }

                // 提取应用版本
                if (jsonData.containsKey("application") && jsonData.get("application") instanceof Map) {
                    Map<String, Object> application = (Map<String, Object>) jsonData.get("application");
                    if (application.containsKey("version")) {
                        device.setVersion((String) application.get("version"));
                    }
                }

                // 提取WiFi名称和设备类型
                if (jsonData.containsKey("board") && jsonData.get("board") instanceof Map) {
                    Map<String, Object> board = (Map<String, Object>) jsonData.get("board");
                    if (board.containsKey("ssid")) {
                        device.setWifiName((String) board.get("ssid"));
                    }
                    if (board.containsKey("type")) {
                        device.setType((String) board.get("type"));
                    }
                }
            } catch (Exception e) {
                logger.debug("JSON解析失败: {}", e.getMessage());
            }

            if (deviceIdAuth == null || !cmsUtils.isMacAddressValid(deviceIdAuth)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "设备ID不正确");
                byte[] responseBytes = JsonUtil.OBJECT_MAPPER.writeValueAsBytes(errorResponse);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setContentLength(responseBytes.length);
                return new ResponseEntity<>(responseBytes, headers, HttpStatus.BAD_REQUEST);
            }

            final String deviceId = deviceIdAuth;
            device.setDeviceId(deviceId);
            device.setLastLogin(new Date().toString());

            // 设置设备IP地址
            device.setIp(CmsUtils.getClientIp(request));
            // 根据设备的IP地址获取地理位置信息
            var ipInfo = CmsUtils.getIPInfoByAddress(device.getIp());
            if (ipInfo != null && ipInfo.getLocation() != null && !ipInfo.getLocation().isEmpty()) {
                device.setLocation(ipInfo.getLocation());
            }

            // 查询设备是否已绑定
            List<SysDevice> queryDevice = deviceService.query(device, new PageFilter());
            Map<String, Object> responseData = new HashMap<>();
            Map<String, Object> firmwareData = new HashMap<>();
            Map<String, Object> serverTimeData = new HashMap<>();

            // 设置服务器时间
            long timestamp = System.currentTimeMillis();
            serverTimeData.put("timestamp", timestamp);
            serverTimeData.put("timezone_offset", 480); // 东八区

            // 设置固件信息
            firmwareData.put("url", cmsUtils.getOtaAddress());
            firmwareData.put("version", "1.0.0");

            // 检查设备是否已绑定
            if (ObjectUtils.isEmpty(queryDevice)) {
                // 设备未绑定，生成验证码
                try {
                    SysDevice codeResult = deviceService.generateCode(device);
                    Map<String, Object> activationData = new HashMap<>();
                    activationData.put("code", codeResult.getCode());
                    activationData.put("message", codeResult.getCode());
                    activationData.put("challenge", deviceId);
                    responseData.put("activation", activationData);
                } catch (Exception e) {
                    logger.error("生成验证码失败", e);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "生成验证码失败");
                    byte[] responseBytes = JsonUtil.OBJECT_MAPPER.writeValueAsBytes(errorResponse);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentLength(responseBytes.length);
                    return new ResponseEntity<>(responseBytes, headers, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                // 设备已绑定，设置连接及认证信息
                if (communicationProtocol.equals("websocket")) {
                    // 设置WebSocket连接信息.
                    String websocketToken = "";//deviceService.generateToken(deviceId);
                    Map<String, Object> websocketData = new HashMap<>();
                    websocketData.put("url", cmsUtils.getWebsocketAddress());
                    websocketData.put("token", websocketToken);
                    responseData.put("websocket", websocketData);
                }
                // 设备已绑定，更新设备状态和信息
                SysDevice boundDevice = queryDevice.get(0);
                // 保留原设备名称，更新其他信息
                device.setDeviceName(boundDevice.getDeviceName());
                device.setState(SysDevice.DEVICE_STATE_ONLINE);

                // 更新设备信息
                deviceService.update(device);
            }

            // 组装响应数据
            responseData.put("firmware", firmwareData);
            responseData.put("server_time", serverTimeData);

            // 手动将响应数据转换为字节数组，以便设置确切的Content-Length
            byte[] responseBytes = JsonUtil.OBJECT_MAPPER.writeValueAsBytes(responseData);

            // 使用ResponseEntity明确设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(responseBytes.length); // 明确设置Content-Length

            return new ResponseEntity<>(responseBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("处理OTA请求失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "处理请求失败: " + e.getMessage());

            try {
                byte[] responseBytes = JsonUtil.OBJECT_MAPPER.writeValueAsBytes(errorResponse);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setContentLength(responseBytes.length);
                return new ResponseEntity<>(responseBytes, headers, HttpStatus.INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                logger.error("生成错误响应失败", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }


    @PostMapping("/ota/activate")
    @ResponseBody
    @Operation(summary = "查询OTA激活状态", description = "返回OTA激活状态")
    public ResponseEntity<String> otaActivate(
        @Parameter(name = "Device-Id", description = "设备唯一标识", required = true, in = ParameterIn.HEADER)
        @RequestHeader("Device-Id") String deviceId) {
        try {
            if(!cmsUtils.isMacAddressValid(deviceId)){
                return ResponseEntity.status(202).build();
            }
            // 解析请求体
            SysDevice sysDevice = deviceService.selectDeviceById(deviceId);
            if (sysDevice == null) {
                return ResponseEntity.status(202).build();
            }
            logger.info("OTA激活结果查询成功, deviceId: {} 激活时间: {}", deviceId, sysDevice.getCreateTime());
        } catch (Exception e) {
            logger.error("OTA激活失败", e);
            return ResponseEntity.status(202).build();
        }
        return ResponseEntity.ok("success");
    }
}