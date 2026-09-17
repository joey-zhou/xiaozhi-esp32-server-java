package com.xiaozhi.dialogue.llm.tool.device;

import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.communication.domain.iot.IotMethod;
import com.xiaozhi.communication.domain.iot.IotMethodParameter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IoT descriptor 缺字段（properties/methods/parameters 为 null）时，工具注册要能跳过缺失部分而不是整体 NPE；
 * 无参方法生成的 schema 不能残留未替换的 ${paramName} 占位符；多参方法要保留全部参数，不能互相覆盖。
 */
class IotServiceRegistrationTest {

    private final IotService iotService = new IotService();

    @Test
    void descriptorWithoutPropertiesAndMethodsDoesNotThrow() {
        IotDescriptor descriptor = new IotDescriptor();
        descriptor.setName("Speaker");
        descriptor.setDescription("扬声器");
        // properties、methods 都不设置，模拟设备只上报了 name/description 的 fuzz 报文

        ToolsSessionHolder holder = new ToolsSessionHolder("s1", null, null);
        invokeRegister("registerPropertiesFunctionTools", holder, descriptor);
        invokeRegister("registerMethodFunctionTools", holder, descriptor);
        // 不抛异常即符合预期
    }

    @Test
    void noParamMethodSchemaHasNoPlaceholderLiteral() {
        IotDescriptor descriptor = new IotDescriptor();
        descriptor.setName("Speaker");
        descriptor.setDescription("扬声器");
        IotMethod method = new IotMethod();
        method.setDescription("静音");
        // parameters 不设置，模拟无参方法
        Map<String, IotMethod> methods = new LinkedHashMap<>();
        methods.put("Mute", method);
        descriptor.setMethods(methods);

        ToolsSessionHolder holder = new ToolsSessionHolder("s1", null, null);
        invokeRegister("registerMethodFunctionTools", holder, descriptor);

        String schema = holder.getFunction("iot_Speaker_Mute").getToolDefinition().inputSchema();
        assertThat(schema).doesNotContain("${");
        assertThat(schema).contains("response_success");
    }

    @Test
    void multiParamMethodSchemaKeepsAllParameters() {
        IotDescriptor descriptor = new IotDescriptor();
        descriptor.setName("Light");
        descriptor.setDescription("灯");
        IotMethod method = new IotMethod();
        method.setDescription("设置颜色");

        Map<String, IotMethodParameter> params = new LinkedHashMap<>();
        IotMethodParameter red = new IotMethodParameter();
        red.setType("integer");
        red.setDescription("红色分量");
        params.put("red", red);
        IotMethodParameter green = new IotMethodParameter();
        green.setType("integer");
        green.setDescription("绿色分量");
        params.put("green", green);
        method.setParameters(params);

        Map<String, IotMethod> methods = new LinkedHashMap<>();
        methods.put("SetColor", method);
        descriptor.setMethods(methods);

        ToolsSessionHolder holder = new ToolsSessionHolder("s1", null, null);
        invokeRegister("registerMethodFunctionTools", holder, descriptor);

        String schema = holder.getFunction("iot_Light_SetColor").getToolDefinition().inputSchema();
        assertThat(schema).contains("\"red\"").contains("\"green\"").contains("response_success");
    }

    private void invokeRegister(String methodName, ToolsSessionHolder holder, IotDescriptor descriptor) {
        ReflectionTestUtils.invokeMethod(iotService, methodName, "s1", holder, descriptor);
    }
}
