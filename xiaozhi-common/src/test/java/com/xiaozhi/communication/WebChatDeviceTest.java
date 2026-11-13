package com.xiaozhi.communication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebChatDeviceTest {

    @Test
    void deviceIdIsThePrefixFollowedByTheUserId() {
        assertThat(WebChatDevice.deviceIdOf(7)).isEqualTo("user_chat_7");
    }

    /** 库的排序规则不区分大小写，User_Chat_7 查得到 user_chat_7 那台设备，判断也得不区分，否则变形写法能绕开登录态校验 */
    @Test
    void recognisesTheIdentityRegardlessOfCase() {
        assertThat(WebChatDevice.isWebChat("user_chat_7")).isTrue();
        assertThat(WebChatDevice.isWebChat("User_Chat_7")).isTrue();
        assertThat(WebChatDevice.isWebChat("94:a9:90:2b:dd:18")).isFalse();
        assertThat(WebChatDevice.isWebChat("user_chat")).isFalse();
        assertThat(WebChatDevice.isWebChat(null)).isFalse();
    }

    @Test
    void userIdIsParsedFromTheSuffix() {
        assertThat(WebChatDevice.userIdOf("user_chat_7")).isEqualTo(7);
        assertThat(WebChatDevice.userIdOf("USER_CHAT_12")).isEqualTo(12);
    }

    @Test
    void userIdIsNullWhenTheIdentityDoesNotParse() {
        assertThat(WebChatDevice.userIdOf("user_chat_")).isNull();
        assertThat(WebChatDevice.userIdOf("user_chat_abc")).isNull();
        assertThat(WebChatDevice.userIdOf("user_chat_99999999999")).isNull();
        assertThat(WebChatDevice.userIdOf("94:a9:90:2b:dd:18")).isNull();
        assertThat(WebChatDevice.userIdOf(null)).isNull();
    }
}
