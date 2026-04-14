package com.xiaozhi.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.server.exception.GlobalExceptionHandler;
import org.mockito.MockedStatic;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mockStatic;

abstract class ControllerTestSupport {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    protected MockMvc buildMockMvc(Object... controllers) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controllers)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .setMessageConverters(
                new ByteArrayHttpMessageConverter(),
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter(objectMapper)
            )
            .build();
    }

    protected String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected MockedStatic<StpUtil> mockLoginUser(int userId) {
        MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::isLogin).thenReturn(true);
        stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
        stpUtil.when(StpUtil::getLoginId).thenReturn(String.valueOf(userId));
        stpUtil.when(StpUtil::getLoginIdAsInt).thenReturn(userId);
        stpUtil.when(StpUtil::getTokenValue).thenReturn("token-" + userId);
        return stpUtil;
    }

    protected MockedStatic<StpUtil> mockNoLoginUser() {
        MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::isLogin).thenReturn(false);
        stpUtil.when(StpUtil::getLoginId).thenReturn(null);
        stpUtil.when(StpUtil::getLoginIdAsInt).thenThrow(new IllegalStateException("无法获取当前登录用户"));
        stpUtil.when(StpUtil::getTokenValue).thenReturn(null);
        return stpUtil;
    }

    /** 反射注入 @Resource 字段，用于 standalone MockMvc 测试 */
    protected static void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject field " + fieldName, e);
        }
    }
}
