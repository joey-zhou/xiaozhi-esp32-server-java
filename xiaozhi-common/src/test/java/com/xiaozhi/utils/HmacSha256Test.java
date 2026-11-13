package com.xiaozhi.utils;

import com.xiaozhi.utils.HmacSha256.Encoding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 编码是各令牌格式的一部分，已经签发出去的令牌（设备 token、文件签名地址）都靠它原样校验，
 * 所以这里用独立算出的值钉住两种编码的字符集与填充。
 */
class HmacSha256Test {

    /** 这组输入的摘要里带 '+'，两种编码在字符集和填充上都不同 */
    private static final String KEY = "key";
    private static final String CONTENT = "x|y";

    @Test
    void standardEncodingKeepsPlusSlashAndPadding() {
        assertThat(HmacSha256.sign(KEY, CONTENT, Encoding.STANDARD))
            .isEqualTo("rJtdmsA1gDcBG9N1dffD2P0DG2J8+QUcWBQ21IBXRYw=");
    }

    @Test
    void urlSafeEncodingSwapsTheAlphabetAndDropsPadding() {
        assertThat(HmacSha256.sign(KEY, CONTENT, Encoding.URL_SAFE))
            .isEqualTo("rJtdmsA1gDcBG9N1dffD2P0DG2J8-QUcWBQ21IBXRYw");
    }

    /** RFC 4231 测试用例 2 */
    @Test
    void matchesTheRfcVector() {
        assertThat(HmacSha256.sign("Jefe", "what do ya want for nothing?", Encoding.STANDARD))
            .isEqualTo("W9zBRr9gdU5qBCQmCJV1x1oAPwidJzmDnexYuWTsOEM=");
    }

    @Test
    void verifyAcceptsOnlyTheExactSignatureInTheSameEncoding() {
        String signature = HmacSha256.sign(KEY, CONTENT, Encoding.URL_SAFE);

        assertThat(HmacSha256.verify(KEY, CONTENT, signature, Encoding.URL_SAFE)).isTrue();
        assertThat(HmacSha256.verify(KEY, CONTENT, signature, Encoding.STANDARD)).isFalse();
        assertThat(HmacSha256.verify(KEY, CONTENT + "z", signature, Encoding.URL_SAFE)).isFalse();
        assertThat(HmacSha256.verify(KEY + "z", CONTENT, signature, Encoding.URL_SAFE)).isFalse();
        assertThat(HmacSha256.verify(KEY, CONTENT, null, Encoding.URL_SAFE)).isFalse();
    }

    /** 空密钥签出来的东西谁都能算，不能当作签名下发 */
    @Test
    void refusesToSignWithABlankKey() {
        assertThatThrownBy(() -> HmacSha256.sign("", CONTENT, Encoding.URL_SAFE))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HmacSha256.sign(null, CONTENT, Encoding.URL_SAFE))
            .isInstanceOf(IllegalStateException.class);
    }
}
