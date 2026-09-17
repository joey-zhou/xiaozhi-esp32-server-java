package com.xiaozhi.role.domain;

import com.xiaozhi.role.domain.vo.VoiceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 局部更新里 -1 与 null 是两回事：-1 是把 TTS / STT 切回本地，null 是这次没改。
 * 之前构造时就把 -1 抹成 null，merge 分不清两者，火山识别切回本地识别保存后还是火山。
 */
class VoiceConfigMergeTest {

    private static final VoiceConfig STORED = new VoiceConfig(3, 4, "xiaoyun", 1.3, 0.8);

    @Test
    void minusOneClearsBackToLocal() {
        VoiceConfig merged = STORED.merge(new VoiceConfig(-1, -1, null, null, null));

        assertThat(merged.ttsId()).isNull();
        assertThat(merged.sttId()).isNull();
        // 没给的字段照旧保留
        assertThat(merged.voiceName()).isEqualTo("xiaoyun");
        assertThat(merged.ttsPitch()).isEqualTo(1.3);
    }

    @Test
    void nullKeepsCurrentValue() {
        VoiceConfig merged = STORED.merge(new VoiceConfig(null, null, "zhichu", null, null));

        assertThat(merged.ttsId()).isEqualTo(3);
        assertThat(merged.sttId()).isEqualTo(4);
        assertThat(merged.voiceName()).isEqualTo("zhichu");
    }

    @Test
    void positiveIdReplacesCurrentValue() {
        VoiceConfig merged = STORED.merge(new VoiceConfig(null, 9, null, null, null));

        assertThat(merged.sttId()).isEqualTo(9);
        assertThat(merged.ttsId()).isEqualTo(3);
    }

    @Test
    void nonPositiveIdsReadAsNullEverywhere() {
        VoiceConfig local = new VoiceConfig(-1, 0, null, null, null);

        assertThat(local.ttsId()).isNull();
        assertThat(local.sttId()).isNull();
        assertThat(local.withDefaults().sttId()).isNull();
        assertThat(local.withDefaults().ttsPitch()).isEqualTo(VoiceConfig.DEFAULT_TTS_PITCH);
    }
}
