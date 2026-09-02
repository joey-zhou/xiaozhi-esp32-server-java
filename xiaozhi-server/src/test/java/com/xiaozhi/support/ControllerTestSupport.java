package com.xiaozhi.support;

import cn.dev33.satoken.exception.NotLoginException;
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

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mockStatic;

/**
 * Controller 测试的公共装配：standaloneSetup + 真实 GlobalExceptionHandler + 真实 Bean Validation，
 * 以及 StpUtil 的登录态静态桩。未登录桩抛的是 sa-token 真实的 NotLoginException，
 * 保证用例断言的是生产链路的 401 映射而不是夹具自编的异常。
 */
public abstract class ControllerTestSupport {

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
        stpUtil.when(StpUtil::getLoginIdAsInt)
            .thenThrow(new NotLoginException("未提供token", "login", NotLoginException.NOT_TOKEN));
        stpUtil.when(StpUtil::getTokenValue).thenReturn(null);
        return stpUtil;
    }
}
