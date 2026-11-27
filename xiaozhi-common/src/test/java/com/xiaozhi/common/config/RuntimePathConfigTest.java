package com.xiaozhi.common.config;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.utils.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 录音布局只在这里定义；存储键始终相对，文件落在 data-dir 下 */
class RuntimePathConfigTest {

    private final RuntimePathConfig config = new RuntimePathConfig();
    private final Instant at = Instant.parse("2026-09-02T12:00:00Z");
    private final LocalDate date = DateUtils.toDateTime(at).toLocalDate();

    @Test
    void recordingsAreKeyedByDateDeviceAndRole() {
        String key = config.recordingKey("94:a9:90:2b:dd:18", 1, "wakeword", at);

        // MAC 地址里的冒号不能进路径
        assertThat(key).startsWith("audio/" + date + "/94-a9-90-2b-dd-18/1/").endsWith("-wakeword.wav");
        assertThat(config.recordingKey("d", 1, MessageBO.SENDER_ASSISTANT, at)).endsWith("-assistant.ogg");
        assertThat(config.recordingKey("d", 1, MessageBO.SENDER_USER, at)).endsWith("-user.wav");
    }

    @Test
    void aDevicesRecordingsOfADayLiveUnderItsDirectoryKey() {
        assertThat(config.recordingKey("aa:bb", 3, MessageBO.SENDER_USER, at))
                .startsWith(config.recordingDirKey("aa:bb", date) + "/");
    }

    @Test
    void keysStayRelativeWhileFilesLandUnderTheDataDir(@TempDir Path dataDir) {
        config.setDataDir(dataDir.toString());

        String key = config.recordingKey("d", 1, MessageBO.SENDER_USER, at);

        assertThat(key).startsWith("audio/");
        assertThat(config.resolveStorageKey(key)).startsWithRaw(dataDir);
        assertThat(config.resolveAudioDir()).startsWithRaw(dataDir);
        assertThat(config.resolveMusicDir()).startsWithRaw(dataDir);
    }
}
