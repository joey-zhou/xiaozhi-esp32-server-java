package com.xiaozhi.music.service;

import com.xiaozhi.common.config.RuntimePathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 音乐上传：只收 mp3 与 playlist.txt，按文件头判型，文件名去掉目录后写进音乐目录 */
class MusicFileServiceTest {

    private static final byte[] ID3_MP3 = {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 0};

    @TempDir
    Path musicDir;

    private MusicFileService service;

    @BeforeEach
    void setUp() {
        RuntimePathConfig paths = mock(RuntimePathConfig.class);
        when(paths.resolveMusicDir()).thenReturn(musicDir);
        service = new MusicFileService(paths);
    }

    @Test
    void anMp3IsSavedUnderItsOwnName() throws IOException {
        assertThat(service.save("歌.mp3", ID3_MP3)).isEqualTo("歌.mp3");
        assertThat(Files.readAllBytes(musicDir.resolve("歌.mp3"))).isEqualTo(ID3_MP3);
    }

    @Test
    void directoriesInTheNameAreDropped() {
        assertThat(service.save("../../etc/歌.mp3", ID3_MP3)).isEqualTo("歌.mp3");
        assertThat(musicDir.resolve("歌.mp3")).exists();
    }

    @Test
    void thePlaylistIsText() {
        assertThat(service.save("playlist.txt", "歌.mp3\n".getBytes())).isEqualTo("playlist.txt");
    }

    @Test
    void otherFilesAreRejected() {
        assertThatThrownBy(() -> service.save("a.exe", ID3_MP3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save("假.mp3", new byte[]{0x4D, 0x5A, 0, 0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是 mp3");
        assertThatThrownBy(() -> service.save("大.mp3", new byte[(int) MusicFileService.MAX_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save("空.mp3", new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
