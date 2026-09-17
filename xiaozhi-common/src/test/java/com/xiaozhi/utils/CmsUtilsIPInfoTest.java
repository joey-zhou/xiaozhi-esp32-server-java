package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 IP 归属查询的两条只有合法的公网 IP 字面量才会外呼，
 * 私网与非法入参在本地出结论，非法串不会被拼进第三方 URL 的路径。
 */
class CmsUtilsIPInfoTest {

    @Test
    void marksPrivateAddressesWithoutCallingOut() {
        CmsUtils.IPInfo info = CmsUtils.getIPInfoByAddress("10.1.2.3");
        assertThat(info).isNotNull();
        assertThat(info.getIsp()).isEqualTo("内网");
        assertThat(info.getLocation()).isEmpty();
    }

    @Test
    void returnsNullForLoopbackAndMalformedInput() {
        assertThat(CmsUtils.getIPInfoByAddress(null)).isNull();
        assertThat(CmsUtils.getIPInfoByAddress("")).isNull();
        assertThat(CmsUtils.getIPInfoByAddress("127.0.0.1")).isNull();
        assertThat(CmsUtils.getIPInfoByAddress("0:0:0:0:0:0:0:1")).isNull();
        assertThat(CmsUtils.getIPInfoByAddress("localhost")).isNull();
        assertThat(CmsUtils.getIPInfoByAddress("8.8.8.8/../admin")).isNull();
        assertThat(CmsUtils.getIPInfoByAddress("evil.com")).isNull();
    }

    @Test
    void cacheOnlyLookupNeverBlocksOnMiss() {
        assertThat(CmsUtils.getIPInfoFromCache("192.168.1.7")).isNotNull();
        assertThat(CmsUtils.getIPInfoFromCache("evil.com")).isNull();
    }
}
