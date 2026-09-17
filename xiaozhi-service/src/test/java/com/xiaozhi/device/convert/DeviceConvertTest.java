package com.xiaozhi.device.convert;

import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.device.model.DeviceProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 投影按名映射到 Resp，列别名与 Resp 字段名错一个字就静默为 null。 */
class DeviceConvertTest {

    private final DeviceConvert convert = new DeviceConvertImpl();

    @Test
    void toRespFromProjectionCarriesEveryColumn() {
        DeviceProjection projection = new DeviceProjection();
        projection.setDeviceId("aa:bb:cc:dd:ee:ff");
        projection.setDeviceName("客厅音箱");
        projection.setRoleId(3);
        projection.setRoleName("小智");
        projection.setState("1");
        projection.setWifiName("home");
        projection.setIp("10.0.0.8");
        projection.setChipModelName("esp32s3");
        projection.setType("dual-board");
        projection.setVersion("2.4.0");
        projection.setLocation("上海");
        projection.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        projection.setUpdateTime(LocalDateTime.of(2026, 9, 1, 0, 0));

        DeviceResp resp = convert.toResp(projection);

        assertThat(resp).usingRecursiveComparison()
            .ignoringFields("sessionId", "code", "audioPath", "mcpList")
            .isEqualTo(projection);
        assertThat(resp.getSessionId()).isNull();
        assertThat(resp.getCode()).isNull();
        assertThat(resp.getAudioPath()).isNull();
        assertThat(resp.getMcpList()).isNull();
    }

    @Test
    void toRespFromVerifyCodeCarriesCodeAndAudioPath() {
        VerifyCodeBO code = new VerifyCodeBO();
        code.setDeviceId("aa:bb:cc:dd:ee:ff");
        code.setSessionId("s-1");
        code.setType("dual-board");
        code.setCode("123456");
        code.setAudioPath("audio/code.wav");

        DeviceResp resp = convert.toResp(code);

        assertThat(resp.getDeviceId()).isEqualTo("aa:bb:cc:dd:ee:ff");
        assertThat(resp.getSessionId()).isEqualTo("s-1");
        assertThat(resp.getType()).isEqualTo("dual-board");
        assertThat(resp.getCode()).isEqualTo("123456");
        assertThat(resp.getAudioPath()).isEqualTo("audio/code.wav");
        assertThat(resp.getDeviceName()).isNull();
        assertThat(resp.getRoleName()).isNull();
    }
}
