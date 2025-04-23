package com.xiaozhi.controller;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.websocket.service.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 设备管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

    @Resource
    private SysDeviceService deviceService;

    @Autowired
    private SysConfigService configService;

    @Autowired
    private SysRoleService roleService;

    @Resource
    private SessionManager sessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 设备查询
     * 
     * @param device
     * @return deviceList
     */
    @GetMapping("/query")
    public Mono<AjaxResult> query(SysDevice device, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    device.setUserId(user.getUserId());
                }

                List<SysDevice> deviceList = deviceService.query(device);
                AjaxResult result = AjaxResult.success();
                result.put("data", new PageInfo<>(deviceList));
                return result;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    /**
     * 设备信息更新
     * 
     * @param device
     * @return
     */
    @PostMapping("/update")
    public Mono<AjaxResult> update(SysDevice device, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                int rows = deviceService.update(device);
                if (rows > 0) {
                    refreshSessionConfig(device);
                    return AjaxResult.success();
                } else {
                    return AjaxResult.error();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error();
            }
        });
    }

    private boolean refreshSessionConfig(SysDevice device) {
        try {
            String deviceId = device.getDeviceId();
            String sessionId = sessionManager.getSessionByDeviceId(deviceId);

            if (sessionId != null) {
                SysDevice updateDevice = device;
                updateDevice.setSessionId(sessionId);
                SysRole roleConfig = new SysRole();
                // 通过roleId获取ttsId
                if (device.getRoleId() != null) {
                    roleConfig = roleService.selectRoleById(device.getRoleId());
                }
                if (device.getModelId() != null) {
                    updateDevice.setModelId(device.getModelId());
                }
                if (device.getSttId() != null) {
                    updateDevice.setSttId(device.getSttId());
                    if (device.getSttId() != -1) {
                        SysConfig sttConfig = configService.selectConfigById(device.getSttId());
                        sessionManager.cacheConfig(sttConfig.getConfigId(), sttConfig);
                    }
                }
                if (roleConfig.getTtsId() != null) {
                    updateDevice.setTtsId(roleConfig.getTtsId());
                    if (device.getTtsId() != -1) {
                        SysConfig tssConfig = configService.selectConfigById(roleConfig.getTtsId());
                        sessionManager.cacheConfig(tssConfig.getConfigId(), tssConfig);
                        updateDevice.setVoiceName(roleConfig.getVoiceName());
                    }
                }
                sessionManager.registerDevice(sessionId, updateDevice);
            }
        } catch (Exception e) {
            logger.error("刷新设备会话配置时发生错误", e);
        }
        return false;
    }

    /**
     * 添加设备
     * 
     * @param code
     */
    @PostMapping("/add")
    public Mono<AjaxResult> add(ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(formData -> {
                    String code = formData.getFirst("code");
                    return Mono.fromCallable(() -> {
                        try {
                            SysDevice device = new SysDevice();
                            device.setCode(code);
                            SysDevice query = deviceService.queryVerifyCode(device);
                            if (query == null) {
                                return AjaxResult.error("无效验证码");
                            }

                            // 从请求属性中获取用户信息
                            SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                            if (user != null) {
                                device.setUserId(user.getUserId());
                            }

                            device.setDeviceId(query.getDeviceId());
                            device.setDeviceName("小智");
                            int row = deviceService.add(device);
                            if (row > 0) {
                                refreshSessionConfig(device);
                                return AjaxResult.success();
                            } else {
                                return AjaxResult.error();
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            return AjaxResult.error();
                        }
                    });
                });
    }

    /**
     * 删除设备
     * 
     * @param device
     * @return
     */
    @PostMapping("/delete")
    public Mono<AjaxResult> delete(SysDevice device, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    device.setUserId(user.getUserId());
                }
                
                // 删除设备
                int rows = deviceService.delete(device);
                
                if (rows > 0) {
                    // 如果设备有会话，清除会话
                    String deviceId = device.getDeviceId();
                    String sessionId = sessionManager.getSessionByDeviceId(deviceId);
                    if (sessionId != null) {
                        sessionManager.closeSession(sessionId);
                    }
                    return AjaxResult.success("删除成功");
                } else {
                    return AjaxResult.error("删除失败");
                }
            } catch (Exception e) {
                logger.error("删除设备时发生错误", e);
                return AjaxResult.error("删除设备时发生错误");
            }
        });
    }

    @PostMapping("/ota")
    public Mono<Map<String, Object>> ota(ServerWebExchange exchange) {
        // 读取请求体内容
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        String requestBody = new String(bytes);
                        SysDevice device = new SysDevice();
                        String deviceIdAuth = null;

                        // 首先从请求头获取设备ID
                        String[] headerKeys = { "device-Id", "mac_address", "uuid" };
                        for (String key : headerKeys) {
                            deviceIdAuth = exchange.getRequest().getHeaders().getFirst(key);
                            if (deviceIdAuth != null) {
                                break;
                            }
                        }

                        // 如果请求头中没有找到，尝试从URI参数中获取
                        if (deviceIdAuth == null) {
                            URI uri = exchange.getRequest().getURI();
                            String query = uri.getQuery();
                            if (query != null) {
                                String[] paramKeys = { "device_id", "mac_address", "uuid" };
                                for (String key : paramKeys) {
                                    String paramPattern = key + "=";
                                    int startIdx = query.indexOf(paramPattern);
                                    if (startIdx >= 0) {
                                        startIdx += paramPattern.length();
                                        int endIdx = query.indexOf('&', startIdx);
                                        deviceIdAuth = endIdx >= 0 ? query.substring(startIdx, endIdx)
                                                : query.substring(startIdx);
                                        break;
                                    }
                                }
                            }
                        }

                        // 解析JSON请求体
                        if (deviceIdAuth == null &&
                                exchange.getRequest().getHeaders().getContentType() != null &&
                                exchange.getRequest().getHeaders().getContentType().toString()
                                        .contains("application/json")) {

                            Map<String, Object> jsonData = objectMapper.readValue(requestBody,
                                    new TypeReference<Map<String, Object>>() {
                                    });

                            // 尝试从JSON中获取设备ID
                            if (jsonData.containsKey("mac_address")) {
                                deviceIdAuth = (String) jsonData.get("mac_address");
                            }
                            if (deviceIdAuth == null && jsonData.containsKey("uuid")) {
                                deviceIdAuth = (String) jsonData.get("uuid");
                            }

                            // 提取chip_model_name
                            if (jsonData.containsKey("chip_model_name")) {
                                device.setChipModelName((String) jsonData.get("chip_model_name"));
                            }

                            // 提取application.version
                            if (jsonData.containsKey("application") && jsonData.get("application") instanceof Map) {
                                Map<String, Object> application = (Map<String, Object>) jsonData.get("application");
                                if (application.containsKey("version")) {
                                    device.setVersion((String) application.get("version"));
                                }
                            }
                        }

                        if (deviceIdAuth == null) {
                            logger.error("设备ID为空");
                            // 直接返回错误信息，不使用AjaxResult包装
                            Map<String, Object> errorResponse = new java.util.HashMap<>();
                            errorResponse.put("error", "设备ID为空");
                            return Mono.just(errorResponse);
                        }

                        final String deviceId = deviceIdAuth;
                        device.setDeviceId(deviceId);
                        device.setLastLogin(new Date().toString());

                        // 查询设备是否已绑定
                        return Mono.fromCallable(() -> deviceService.query(device))
                                .flatMap(devices -> {
                                    Map<String, Object> responseData = new java.util.HashMap<>();
                                    Map<String, Object> firmwareData = new java.util.HashMap<>();
                                    Map<String, Object> serverTimeData = new java.util.HashMap<>();
                                    Map<String, Object> websocketData = new java.util.HashMap<>();

                                    // 设置服务器时间
                                    long timestamp = System.currentTimeMillis();
                                    serverTimeData.put("timestamp", timestamp);
                                    serverTimeData.put("timezone_offset", 480); // 东八区

                                    // 设置固件信息
                                    firmwareData.put("url", "");
                                    firmwareData.put("version", "1.0.0");

                                    // 设置WebSocket token和address
                                    websocketData.put("url", "ws://14.103.233.248/ws/xiaozhi/v1/");
                                    websocketData.put("token", "");

                                    // 检查设备是否已绑定
                                    if (devices.isEmpty()) {
                                        // 设备未绑定，生成验证码
                                        try {
                                            SysDevice codeResult = deviceService.generateCode(device);
                                            // 只有在需要验证码时才添加activation字段
                                            Map<String, Object> activationData = new java.util.HashMap<>();
                                            activationData.put("code", codeResult.getCode());
                                            activationData.put("message", codeResult.getCode());
                                            responseData.put("activation", activationData);

                                            // 如果是新设备，更新设备信息
                                            if (devices.isEmpty()) {
                                                deviceService.update(device);
                                            }
                                        } catch (Exception e) {
                                            logger.error("生成验证码失败", e);
                                            Map<String, Object> errorResponse = new java.util.HashMap<>();
                                            errorResponse.put("error", "生成验证码失败");
                                            return Mono.just(errorResponse);
                                        }
                                    } else {
                                        // 设备已绑定，不需要返回activation字段
                                        SysDevice boundDevice = devices.get(0);

                                        // 更新设备状态
                                        deviceService.update(device.setDeviceName(boundDevice.getDeviceName()).setState("1"));
                                    }

                                    // 组装响应数据 - 只包含必要的字段
                                    responseData.put("firmware", firmwareData);
                                    responseData.put("serverTime", serverTimeData);
                                    responseData.put("websocket", websocketData);

                                    return Mono.just(responseData);
                                });
                    } catch (Exception e) {
                        logger.error("处理OTA请求失败", e);
                        Map<String, Object> errorResponse = new java.util.HashMap<>();
                        errorResponse.put("error", "处理OTA请求失败");
                        return Mono.just(errorResponse);
                    }
                });
    }
}