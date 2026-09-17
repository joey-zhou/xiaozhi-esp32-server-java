package com.xiaozhi.role.convert;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.role.domain.vo.LlmConfig;
import com.xiaozhi.role.domain.vo.VoiceConfig;
import com.xiaozhi.role.model.RoleProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投影按名映射到 Resp，列别名与 Resp 字段名错一个字就静默为 null；
 * DO → BO 则要把该列为 NULL 的历史行补成领域模型给的默认值。
 */
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
    void toBOFillsTheDomainDefaultsForLegacyNullColumns() {
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleId(7);
        roleDO.setRoleName("小智");

        RoleBO bo = convert.toBO(roleDO);

        // 五个默认值只有领域模型一份，读路径引用它，不再自带字面量
        assertThat(bo.getTemperature()).isEqualTo(LlmConfig.DEFAULT_TEMPERATURE);
        assertThat(bo.getTopP()).isEqualTo(LlmConfig.DEFAULT_TOP_P);
        assertThat(bo.getTtsPitch()).isEqualTo(VoiceConfig.DEFAULT_TTS_PITCH);
        assertThat(bo.getTtsSpeed()).isEqualTo(VoiceConfig.DEFAULT_TTS_SPEED);
        assertThat(bo.getInactiveTimeoutSeconds()).isEqualTo(Role.DEFAULT_INACTIVE_TIMEOUT_SECONDS);
    }

    @Test
    void toBOKeepsStoredValuesInsteadOfDefaults() {
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleId(7);
        roleDO.setTemperature(0.2);
        roleDO.setTopP(0.5);
        roleDO.setTtsPitch(1.3);
        roleDO.setTtsSpeed(0.8);
        roleDO.setInactiveTimeoutSeconds(0);

        RoleBO bo = convert.toBO(roleDO);

        assertThat(bo.getTemperature()).isEqualTo(0.2);
        assertThat(bo.getTopP()).isEqualTo(0.5);
        assertThat(bo.getTtsPitch()).isEqualTo(1.3);
        assertThat(bo.getTtsSpeed()).isEqualTo(0.8);
        // 0 是「关闭空闲超时」的合法取值，不能被当成没填而补成 60
        assertThat(bo.getInactiveTimeoutSeconds()).isZero();
    }
}
