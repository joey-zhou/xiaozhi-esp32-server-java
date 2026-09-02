package com.xiaozhi.music;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住音乐上传接口的白名单（只收 .mp3 与 playlist.txt）与落盘位置：文件写到 RuntimePathConfig.musicDir 下，
 * 文件名保持原名。用例把 musicDir 指到临时目录，避免真实写进工作目录。
 */
class MusicControllerTest extends ControllerTestSupport {

    @TempDir
    Path musicDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RuntimePathConfig runtimePathConfig = new RuntimePathConfig();
        runtimePathConfig.setMusicDir(musicDir.toString());
        MusicController controller = new MusicController();
        ReflectionTestUtils.setField(controller, "runtimePathConfig", runtimePathConfig);
        mockMvc = buildMockMvc(controller);
    }

    @Test
    void uploadMusicRejectsEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/file/music").file(mp3("song.mp3", new byte[0])))
            .andExpect(status().isOk())
            .andExpect(content().string("上传失败"));
    }

    @Test
    void uploadMusicRejectsDisallowedExtension() throws Exception {
        mockMvc.perform(multipart("/api/file/music").file(mp3("song.wav", "abc".getBytes())))
            .andExpect(status().isOk())
            .andExpect(content().string("上传失败"));

        assertThat(Files.exists(musicDir.resolve("song.wav"))).isFalse();
    }

    @Test
    void uploadMusicStoresAllowedMp3File() throws Exception {
        mockMvc.perform(multipart("/api/file/music").file(mp3("song-test.mp3", "abc".getBytes())))
            .andExpect(status().isOk())
            .andExpect(content().string("song-test.mp3，上传成功"));

        assertThat(musicDir.resolve("song-test.mp3")).hasBinaryContent("abc".getBytes());
    }

    @Test
    void uploadMusicStoresPlaylistFile() throws Exception {
        mockMvc.perform(multipart("/api/file/music").file(mp3("playlist.txt", "song-test.mp3".getBytes())))
            .andExpect(status().isOk())
            .andExpect(content().string("playlist.txt，上传成功"));

        assertThat(musicDir.resolve("playlist.txt")).hasBinaryContent("song-test.mp3".getBytes());
    }

    private static MockMultipartFile mp3(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, "audio/mpeg", content);
    }
}
