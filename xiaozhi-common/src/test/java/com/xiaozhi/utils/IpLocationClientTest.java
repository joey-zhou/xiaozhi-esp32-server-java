package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 IP 归属查询的两条只有合法的公网 IP 字面量才会外呼，
 * 私网与非法入参在本地出结论，非法串不会被拼进第三方 URL 的路径。
 */
class IpLocationClientTest {

    @Test
    void marksPrivateAddressesWithoutCallingOut() {
        IpLocationClient.IPInfo info = IpLocationClient.getIPInfoByAddress("10.1.2.3");
        assertThat(info).isNotNull();
        assertThat(info.getIsp()).isEqualTo("内网");
        assertThat(info.getLocation()).isEmpty();
    }

    @Test
    void returnsNullForLoopbackAndMalformedInput() {
        assertThat(IpLocationClient.getIPInfoByAddress(null)).isNull();
        assertThat(IpLocationClient.getIPInfoByAddress("")).isNull();
        assertThat(IpLocationClient.getIPInfoByAddress("127.0.0.1")).isNull();
        assertThat(IpLocationClient.getIPInfoByAddress("0:0:0:0:0:0:0:1")).isNull();
        assertThat(IpLocationClient.getIPInfoByAddress("localhost")).isNull();
        assertThat(IpLocationClient.getIPInfoByAddress("8.8.8.8/../admin")).isNull();
        assertThat(IpLocationClient.getIPInfoByAddress("evil.com")).isNull();
    }

    @Test
    void cacheOnlyLookupNeverBlocksOnMiss() {
        assertThat(IpLocationClient.getIPInfoFromCache("192.168.1.7")).isNotNull();
        assertThat(IpLocationClient.getIPInfoFromCache("evil.com")).isNull();
    }
}
