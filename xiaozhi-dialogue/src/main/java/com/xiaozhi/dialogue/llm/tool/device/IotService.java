package com.xiaozhi.dialogue.llm.tool.device;

import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.communication.domain.iot.IotProperty;
import com.xiaozhi.communication.domain.iot.IotState;
import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
/**
 * Iot服务 - 负责iot处理和WebSocket发送
 */
@Slf4j
@Service
public class IotService {
    private static final String TAG = "IotService";

    @Resource
    private SessionManager sessionManager;

    @Resource
    private MessageSender messageService;

    /**
     * 处理iot设备描述信息，形成function_call，注册进sessionManager，用于后续的llm调用及设备调用
     *
     * @param sessionId   会话ID
     * @param descriptors iot设备描述消息内容
     */
    public void handleDeviceDescriptors(String sessionId, List<IotDescriptor> descriptors) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        for (var descriptor : descriptors) {
            chatSession.getIotDescriptors().put(descriptor.getName(), descriptor);
            registerFunctionTools(sessionId, descriptor);
        }
    }

    /**
     * 处理iot设备状态变更信息，可用于更新设备状态或进行其他操作
     *
     * @param sessionId 会话ID
     * @param states    iot状体消息内容
     */
    public void handleDeviceStates(String sessionId, List<IotState> states) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        for (var state : states) {
            var iotDescriptor = chatSession.getIotDescriptors().get(state.getName());
            if (iotDescriptor == null) {
                log.error("[{}] - SessionId: {} 未找到设备: {} 的描述信息", TAG, sessionId, state.getName());
                continue;
            }
            for (var stateProp : state.getState().entrySet()) {
                var propName = stateProp.getKey();
                var propValue = stateProp.getValue();
                var property = iotDescriptor.getProperties().get(propName);
                if (property != null) {
                    property.setValue(propValue);
                    log.info("[{}] - SessionId: {} handleDeviceStates 物联网状态更新: {} , {} = {}", TAG, sessionId, state.getName(), propName, propValue);
                } else {
                    log.error("[{}] - SessionId: {} handleDeviceStates 未找到设备 {} 的属性 {}", TAG, sessionId, state.getName(), propName);
                }
            }
        }
    }

    /**
     * 获取物联网状态
     *
     * @param sessionId    会话ID
     * @param iotName      iot设备名称
     * @param propertyName 属性名称
     * @return 属性值，如未找到则返回null
     */
    public Object getIotStatus(String sessionId, String iotName, String propertyName) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        var iotDescriptor = chatSession.getIotDescriptors().get(iotName);
        if (iotDescriptor != null) {
            IotProperty property = iotDescriptor.getProperties().get(propertyName);
            if (property != null) {
                return property.getValue();
            } else {
                log.error("[{}] - SessionId: {} getIotStatus 未找到设备 {} 的属性 {}", TAG, sessionId, iotName, propertyName);
            }
        } else {
            log.error("[{}] - SessionId: {} getIotStatus 未找到设备 {}", TAG, sessionId, iotName);
        }
        return null;
    }

    /**
     * 设置物联网状态
     *
     * @param sessionId    会话ID
     * @param iotName      iot设备名称
     * @param propertyName 属性名称
     * @param value        属性值
     * @return 是否设置成功
     */
    public boolean setIotStatus(String sessionId, String iotName, String propertyName, Object value) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        var iotDescriptor = chatSession.getIotDescriptors().get(iotName);
        if (iotDescriptor != null) {
            IotProperty property = iotDescriptor.getProperties().get(propertyName);
            if (property != null) {
                // 类型检查
                boolean typeCheck = false;
                if (property.getType().equalsIgnoreCase(JsonNodeType.OBJECT.name())) {
                    typeCheck = true;
                } else if (value instanceof Number && property.getType().equalsIgnoreCase(JsonNodeType.NUMBER.name())) {
                    typeCheck = true;
                } else if (value instanceof String && property.getType().equalsIgnoreCase(JsonNodeType.STRING.name())) {
                    typeCheck = true;
                } else if (value instanceof Boolean && property.getType().equalsIgnoreCase(JsonNodeType.BOOLEAN.name())) {
                    typeCheck = true;
                }
                if (!typeCheck) {
                    log.error("[{}] - SessionId: {} setIotStatus 属性: {} 的值类型不匹配, 注册类型: {}, 入参类型: {}", TAG, sessionId, propertyName,
                            property.getType(), value.getClass().getSimpleName());
                    return false;
                }
                property.setValue(value);
                log.info("[{}] - SessionId: {} setIotStatus 物联网状态更新: {} , {} = {}", TAG, sessionId, iotName, propertyName, value);
                sendIotMessage(sessionId, iotName, propertyName, Collections.singletonMap(propertyName, value));
                return true;
            }
        }
        log.error("[{}] - SessionId: {} setIotStatus 未找到设备 {} 的属性 {}", TAG, sessionId, iotName, propertyName);
        return false;
    }

    /**
     * 发送iot消息到设备
     *
     * @param sessionId  会话ID
     * @param iotName    iot设备名称
     * @param methodName 方法名称
     * @param parameters 方法参数
     */
    public boolean sendIotMessage(String sessionId, String iotName, String methodName, Map<String, Object> parameters) {
        try {
            log.info("[{}] - SessionId: {}, message send iotName: {}, methodName: {}, parameters: {}", TAG, sessionId,
                    iotName, methodName, JsonUtil.toJson(parameters));
            ChatSession chatSession = sessionManager.getSession(sessionId);
            IotDescriptor iotDescriptor = chatSession.getIotDescriptors().get(iotName);
            if (iotDescriptor != null && iotDescriptor.getMethods().containsKey(methodName)) {
                Map<String, Object> command = new HashMap<>();
                command.put("name", iotName);
                command.put("method", methodName);
                command.put("parameters", parameters);
                messageService.sendIotCommandMessage(chatSession, Collections.singletonList(command));
                return true;
            } else {
                log.error("[{}] - SessionId: {}, {} method not found: {}", TAG, sessionId, iotName, methodName);
            }
        } catch (Exception e) {
            log.error("[{}] - SessionId: {}, error sending Iot message", TAG, sessionId, e);
        }
        return false;
    }

    /**
     * 注册iot设备的函数到FunctionHolder
     *
     * @param sessionId     会话ID
     * @param iotDescriptor session绑定的FunctionHolder
     */
    private void registerFunctionTools(String sessionId, IotDescriptor iotDescriptor) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        ToolsSessionHolder toolsSessionHolder = chatSession != null ? chatSession.getToolsSessionHolder() : null;
        registerPropertiesFunctionTools(sessionId, toolsSessionHolder, iotDescriptor);
        registerMethodFunctionTools(sessionId, toolsSessionHolder, iotDescriptor);
    }

    /**
     * 注册iot设备的属性的查询方法到FunctionHolder
     *
     * @param sessionId          会话ID
     * @param toolsSessionHolder session绑定的FunctionHolder
     * @param iotDescriptor      iot信息
     */
    private void registerPropertiesFunctionTools(String sessionId, ToolsSessionHolder toolsSessionHolder, IotDescriptor iotDescriptor) {
        //遍历properties，生成FunctionCallTool
        var iotName = iotDescriptor.getName();
        for (var entry : iotDescriptor.getProperties().entrySet()) {
            var propName = entry.getKey();
            var propInfo = entry.getValue();
            // 创建函数名称，格式：iot_get_{IoTName}_{PropName}
            var funcName = "iot_get_" + iotName.toLowerCase() + "_" + propName.toLowerCase();
            var toolCallback = FunctionToolCallback
                    .builder(funcName, (Map<String, String> params, ToolContext toolContext) -> {
                        Object value = getIotStatus(sessionId, iotName, propName);
                        if (value != null) {
                            // 获取参数
                            String response_success = params.get("response_success");
                            //如果有success参数，并且有{value}占位符，用相关参数替换
                            if (response_success != null) {
                                if (response_success.contains("{value}")) {
                                    response_success = response_success.replace("{value}", String.valueOf(value));
                                }
                            } else {
                                response_success = "当前的设置为" + value;
                            }
                            return response_success;
                        } else {
                            return "无法获取设置";
                        }
                    })
                    .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                    .description("查询" + iotName + "的" + propInfo.getDescription())
                    .inputSchema("""
                                {
                                    "type": "object",
                                    "properties": {
                                        "response_success": {
                                            "type": "string",
                                            "description": "查询成功时的友好回复，必须使用{value}作为占位符表示查询到的值"
                                        }
                                    },
                                    "required": ["response_success"]
                                }
                            """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
            // 注册到当前会话的函数持有者
            toolsSessionHolder.registerFunction(funcName, toolCallback);
        }

    }

    /**
     * 注册iot设备的可调用方法到FunctionHolder
     *
     * @param sessionId          会话ID
     * @param toolsSessionHolder FunctionHolder实例
     * @param iotDescriptor      iot信息
     */
    private void registerMethodFunctionTools(String sessionId, ToolsSessionHolder toolsSessionHolder, IotDescriptor iotDescriptor) {
        // 遍历methods，生成FunctionCallTool
        var iotName = iotDescriptor.getName();

        for (var entry : iotDescriptor.getMethods().entrySet()) {
            var methodName = entry.getKey();
            var method = entry.getValue();
            // 创建函数名称，格式：iot_{IoTName}_{MethodName}
            var funcName = "iot_" + iotName + "_" + methodName;

            Map<String, String> valueMap = new HashMap<>();
            //获取iotMethod方法参数，添加到函数参数中。 iot方法都是单参数
            for (var paramEntry : method.getParameters().entrySet()) {
                var paramName = paramEntry.getKey();
                var paramInfo = paramEntry.getValue();
                valueMap.put("paramName", paramName);
                valueMap.put("paramType", paramInfo.getType());
                valueMap.put("paramDescription", paramInfo.getDescription());
            }
            String inputSchema = StringSubstitutor.replace("""
                        {
                            "type": "object",
                            "properties": {
                                "${paramName}": {
                                    "type": "${paramType}",
                                    "description": "${paramDescription}"
                                },
                                "response_success": {
                                    "type": "string",
                                    "description": "操作成功时的友好回复,关于该设备的操作结果，设备名称使用description中的名称，不要出现占位符"
                                }
                            },
                            "required": ["${paramName}", "response_success"]
                        }
                    """, valueMap);

            var toolCallback = FunctionToolCallback
                    .builder(funcName, (Map<String, Object> params, ToolContext toolContext) -> {
                        String actFuncName = funcName.replace("iot_" + iotName + "_", ""); // 原始方法调用，去掉iot_iotName_前缀
                        String response_success = (String) params.get("response_success");
                        params.remove("response_success"); // 移除response_success参数，避免传递给设备
                        boolean result = sendIotMessage(sessionId, iotName, actFuncName, params);
                        if (result) {
                            // 获取参数
                            if (response_success == null || response_success.isEmpty()) {
                                response_success = "操作成功";
                            }
                            return response_success;
                        } else {
                            return "操作失败";
                        }
                    })
                    .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                    .description(iotDescriptor.getDescription() + " - " + method.getDescription())
                    .inputSchema(inputSchema)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
            // 注册到当前会话的函数持有者
            toolsSessionHolder.registerFunction(funcName, toolCallback);
        }
    }

}
