package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住安全用途取 IP 的没配可信代理时任何代理头都不采信，
 * 配了可信代理也只认最右侧那一跳非代理地址，伪造头不能改变限流与审计的 key。
 */
class RequestContextUtilsTrustedIpTest {

    private static final List<String> PROXIES = List.of("10.0.0.0/8", "127.0.0.1");

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void ignoresProxyHeadersWhenNoTrustedProxyConfigured() {
        assertThat(RequestContextUtils.getTrustedClientIp(request("203.0.113.9", "1.1.1.1"), List.of()))
            .isEqualTo("203.0.113.9");
        assertThat(RequestContextUtils.getTrustedClientIp(request("203.0.113.9", "1.1.1.1"), null))
            .isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresProxyHeadersWhenPeerIsNotATrustedProxy() {
        assertThat(RequestContextUtils.getTrustedClientIp(request("203.0.113.9", "1.1.1.1"), PROXIES))
            .isEqualTo("203.0.113.9");
    }

    @Test
    void takesRightmostNonProxyHopWhenPeerIsTrusted() {
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "8.8.8.8"), PROXIES))
            .isEqualTo("8.8.8.8");
        // 客户端自己伪造的左侧内容不采信
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "1.1.1.1, 8.8.8.8"), PROXIES))
            .isEqualTo("8.8.8.8");
        // 多层可信代理时跳过代理自身的地址
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "8.8.8.8, 10.9.9.9"), PROXIES))
            .isEqualTo("8.8.8.8");
    }

    @Test
    void readsEveryForwardedForHeaderInArrivalOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        // 客户端自己带的一行在前，代理新增的一行在后，取值必须落在代理写的那一行上
        request.addHeader("X-Forwarded-For", "9.9.9.9");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        assertThat(RequestContextUtils.getTrustedClientIp(request, PROXIES)).isEqualTo("8.8.8.8");
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedForIsUnusable() {
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", null), PROXIES))
            .isEqualTo("10.1.2.3");
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "   "), PROXIES))
            .isEqualTo("10.1.2.3");
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "8.8.8.8, evil"), PROXIES))
            .isEqualTo("10.1.2.3");
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "10.0.0.5"), PROXIES))
            .isEqualTo("10.1.2.3");
    }

    @Test
    void matchesTrustedProxyAcrossIpv6AndMappedForms() {
        assertThat(RequestContextUtils.getTrustedClientIp(request("0:0:0:0:0:0:0:1", "8.8.8.8"), List.of("::1")))
            .isEqualTo("8.8.8.8");
        assertThat(RequestContextUtils.getTrustedClientIp(request("::ffff:10.1.2.3", "8.8.8.8"), PROXIES))
            .isEqualTo("8.8.8.8");
    }

    @Test
    void ignoresMalformedTrustedProxyEntries() {
        assertThat(RequestContextUtils.getTrustedClientIp(request("10.1.2.3", "8.8.8.8"), List.of("10.0.0.0/x")))
            .isEqualTo("10.1.2.3");
    }

    @Test
    void recognisesOnlyIpLiterals() {
        assertThat(RequestContextUtils.isIpLiteral("1.2.3.4")).isTrue();
        assertThat(RequestContextUtils.isIpLiteral("2001:db8::ff00:42:8329")).isTrue();
        assertThat(RequestContextUtils.isIpLiteral("256.1.1.1")).isFalse();
        assertThat(RequestContextUtils.isIpLiteral("evil.com")).isFalse();
        assertThat(RequestContextUtils.isIpLiteral("8.8.8.8/../admin")).isFalse();
        assertThat(RequestContextUtils.isIpLiteral(null)).isFalse();
    }
}
