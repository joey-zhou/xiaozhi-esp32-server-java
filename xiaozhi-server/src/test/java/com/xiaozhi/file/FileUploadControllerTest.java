package com.xiaozhi.file;

import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住通用上传接口：文件按 类型/yyyy/MM/dd 分目录存放、文件名换成随机名，
 * 本地存储返回的相对路径要拼成对外可访问的完整 URL，存储层 IO 失败对外统一成 500 文案。
 */
@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private StorageServiceFactory storageServiceFactory;

    @Mock
    private StorageService storageService;

    @Mock
    private ServerAddressProvider serverAddressProvider;

    private FileUploadController fileUploadController;

    @BeforeEach
    void setUp() {
        fileUploadController = new FileUploadController();
        ReflectionTestUtils.setField(fileUploadController, "storageServiceFactory", storageServiceFactory);
        ReflectionTestUtils.setField(fileUploadController, "serverAddressProvider", serverAddressProvider);
        mockMvc = buildMockMvc(fileUploadController);
    }

    @Test
    void uploadFileReturnsFullUrlForRelativeStoragePath() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "png".getBytes());
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(), anyString(), anyString())).thenReturn("uploads/image/avatar.png");
        when(storageService.getProvider()).thenReturn("local");
        when(serverAddressProvider.getServerAddress()).thenReturn("https://server.test");

        mockMvc.perform(multipart("/api/file/upload").file(file).param("type", "image"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("上传成功"))
            .andExpect(jsonPath("$.data.fileName").value("avatar.png"))
            .andExpect(jsonPath("$.data.relativePath").value("uploads/image/avatar.png"))
            .andExpect(jsonPath("$.data.url").value("https://server.test/uploads/image/avatar.png"));

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(any(), pathCaptor.capture(), nameCaptor.capture());
        // 目录按 类型/yyyy/MM/dd 分层，不照抄生产代码里的 LocalDate.now() 表达式，避免跨午夜误判
        assertThat(pathCaptor.getValue()).matches("image/\\d{4}/\\d{2}/\\d{2}");
        // 落盘文件名换成去掉横线的 UUID，只保留原扩展名
        assertThat(nameCaptor.getValue()).matches("[0-9a-f]{32}\\.png");
    }

    @Test
    void uploadFileRejectsUnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "png".getBytes());

        mockMvc.perform(multipart("/api/file/upload").file(file).param("type", "script"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("不支持的文件类型分类: script"));
    }

    @Test
    void uploadFileWrapsStorageIOException() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "png".getBytes());
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(), anyString(), anyString())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/file/upload").file(file).param("type", "image"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("文件上传失败，请稍后重试"));
    }
}
