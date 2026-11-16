package com.xiaozhi.role.domain;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.role.domain.vo.AudioConfig;
import com.xiaozhi.role.domain.vo.LlmConfig;
import com.xiaozhi.role.domain.vo.VoiceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** VAD 阈值与采样参数一样：没给的补 RoleBO 的默认，给了的原样保留 */
class AudioConfigDefaultsTest {

    @Test
    void newRoleWithoutThresholdsGetsTheDefaults() {
        Role role = Role.newRole(7, "管家", "", null, LlmConfig.defaults(), VoiceConfig.defaults(),
            new AudioConfig(null, null, null, null), false, null);

        assertThat(role.getAudioConfig()).isEqualTo(new AudioConfig(RoleBO.DEFAULT_VAD_ENERGY_TH,
            RoleBO.DEFAULT_VAD_SPEECH_TH, RoleBO.DEFAULT_VAD_SILENCE_TH, RoleBO.DEFAULT_VAD_SILENCE_MS));
    }

    @Test
    void givenThresholdsAreKept() {
        AudioConfig given = new AudioConfig(0.02f, 0.6f, 0.2f, 1200);

        assertThat(given.withDefaults()).isEqualTo(given);
    }
}
