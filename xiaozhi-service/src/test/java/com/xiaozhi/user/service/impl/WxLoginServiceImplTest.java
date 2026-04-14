package com.xiaozhi.user.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WxLoginServiceImplTest {

    @Test
    void getWxLoginInfoParsesOpenIdSessionKeyAndUnionId() {
        WxLoginServiceImpl wxLoginService = new WxLoginServiceImpl();
        ReflectionTestUtils.setField(wxLoginService, "appid", "app-1");
        ReflectionTestUtils.setField(wxLoginService, "secret", "secret-1");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(wxLoginService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=app-1&secret=secret-1&js_code=code-1&grant_type=authorization_code";
        server.expect(requestTo(url))
            .andRespond(withSuccess("""
                {"openid":"openid-1","session_key":"session-1","unionid":"union-1"}
                """, MediaType.APPLICATION_JSON));

        Map<String, String> result = wxLoginService.getWxLoginInfo("code-1");

        assertThat(result).containsEntry("openid", "openid-1");
        assertThat(result).containsEntry("session_key", "session-1");
        assertThat(result).containsEntry("unionid", "union-1");
        server.verify();
    }

    @Test
    void getWxLoginInfoThrowsWhenResponseCannotBeParsed() {
        WxLoginServiceImpl wxLoginService = new WxLoginServiceImpl();
        ReflectionTestUtils.setField(wxLoginService, "appid", "app-1");
        ReflectionTestUtils.setField(wxLoginService, "secret", "secret-1");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(wxLoginService, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=app-1&secret=secret-1&js_code=code-1&grant_type=authorization_code";
        server.expect(requestTo(url))
            .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> wxLoginService.getWxLoginInfo("code-1"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("解析微信登录响应失败");

        server.verify();
    }
}
