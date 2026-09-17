package com.xiaozhi.verifycode.convert;

import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备绑定全靠这张表定位设备：code 找错行会把设备绑到别人账号下，
 * audioPath 丢了会让绑定成功后的语音文件删不掉，createTime 丢了 10 分钟有效期就失效。
 * 这个转换器此前在整个测试套件里一次都没被真正执行过。
 */
class VerifyCodeConvertTest {

    private final VerifyCodeConvert convert = Mappers.getMapper(VerifyCodeConvert.class);

    @Test
    void toBOCarriesEveryFieldTheBindingPathReadsBack() {
        LocalDateTime createTime = LocalDateTime.of(2026, 9, 9, 10, 30);
        VerifyCodeDO source = new VerifyCodeDO();
        source.setCodeId(11);
        source.setCode("123456");
        source.setType("device");
        source.setEmail("someone@example.com");
        source.setDeviceId("dev-1");
        source.setSessionId("session-1");
        source.setAudioPath("audio/verify/abc.wav");
        source.setCreateTime(createTime);

        VerifyCodeBO bo = convert.toBO(source);

        assertThat(bo.getCode()).isEqualTo("123456");
        assertThat(bo.getType()).isEqualTo("device");
        assertThat(bo.getEmail()).isEqualTo("someone@example.com");
        assertThat(bo.getDeviceId()).isEqualTo("dev-1");
        assertThat(bo.getSessionId()).isEqualTo("session-1");
        assertThat(bo.getAudioPath())
            .as("audioPath 丢了，绑定成功后那条验证码语音就没人删，文件会一直留着")
            .isEqualTo("audio/verify/abc.wav");
        assertThat(bo.getCreateTime())
            .as("createTime 丢了，10 分钟有效窗口的判定就永远拿不到基准时间")
            .isEqualTo(createTime);
    }

    @Test
    void toBOOfNullStaysNull() {
        assertThat(convert.toBO(null)).isNull();
    }
}
