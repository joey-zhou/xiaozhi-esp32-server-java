package com.xiaozhi.controller;

import com.xiaozhi.music.MusicController;
import com.xiaozhi.common.config.RuntimePathConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MusicControllerTest extends ControllerTestSupport {

    private final RuntimePathConfig runtimePathConfig = new RuntimePathConfig();
    private final Path musicDir = Path.of(runtimePathConfig.getMusicDir());

    private MockMvc mockMvc;
    private Path uploadedFile;

    @BeforeEach
    void setUp() {
        MusicController controller = new MusicController();
        injectField(controller, "runtimePathConfig", runtimePathConfig);
        mockMvc = buildMockMvc(controller);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile);
        }
    }

    @Test
    void uploadMusicRejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "song.mp3", "audio/mpeg", new byte[0]);

        mockMvc.perform(multipart("/api/file/music").file(file))
            .andExpect(status().isOk())
            .andExpect(content().string("上传失败"));
    }

    @Test
    void uploadMusicStoresAllowedMp3File() throws Exception {
        String fileName = "song-test.mp3";
        uploadedFile = musicDir.resolve(fileName);
        MockMultipartFile file = new MockMultipartFile("file", fileName, "audio/mpeg", "abc".getBytes());

        mockMvc.perform(multipart("/api/file/music").file(file))
            .andExpect(status().isOk())
            .andExpect(content().string(fileName + "，上传成功"));

        assertThat(Files.exists(uploadedFile)).isTrue();
    }
}
