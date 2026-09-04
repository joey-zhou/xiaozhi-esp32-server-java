package com.xiaozhi.role.convert;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.role.model.RoleProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 投影按名映射到 Resp，列别名与 Resp 字段名错一个字就静默为 null。 */
class RoleConvertTest {

    private final RoleConvert convert = new RoleConvertImpl();

    @Test
    void toRespFromProjectionCarriesEveryColumn() {
        RoleProjection projection = new RoleProjection();
        projection.setRoleId(7);
        projection.setAvatar("avatar/role.png");
        projection.setRoleName("小智");
        projection.setRoleDesc("语音助手");
        projection.setVoiceName("xiaoyun");
        projection.setTtsPitch(1.1);
        projection.setTtsSpeed(0.9);
        projection.setState("1");
        projection.setTtsId(3);
        projection.setModelId(5);
        projection.setModelName("qwen-plus");
        projection.setSttId(4);
        projection.setTemperature(0.6);
        projection.setTopP(0.8);
        projection.setVadEnergyTh(0.1f);
        projection.setVadSpeechTh(0.2f);
        projection.setVadSilenceTh(0.3f);
        projection.setVadSilenceMs(500);
        projection.setInactiveTimeoutSeconds(90);
        projection.setModelProvider("aliyun");
        projection.setTtsProvider("edge");
        projection.setIsDefault("1");
        projection.setTotalDevice(2);
        projection.setMemoryType("summary");
        projection.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        projection.setUpdateTime(LocalDateTime.of(2026, 9, 1, 0, 0));

        RoleResp resp = convert.toResp(projection);

        assertThat(resp).usingRecursiveComparison().isEqualTo(projection);
    }

    @Test
    void toRespFromBOLeavesJoinColumnsNull() {
        RoleBO bo = new RoleBO();
        bo.setRoleId(7);
        bo.setRoleName("小智");
        bo.setModelId(5);

        RoleResp resp = convert.toResp(bo);

        assertThat(resp.getRoleId()).isEqualTo(7);
        assertThat(resp.getModelId()).isEqualTo(5);
        assertThat(resp.getModelName()).isNull();
        assertThat(resp.getModelProvider()).isNull();
        assertThat(resp.getTtsProvider()).isNull();
        assertThat(resp.getTotalDevice()).isNull();
    }
}
