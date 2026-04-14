package com.xiaozhi.controller;

import com.xiaozhi.file.FileUploadController;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        String relativePath = "image/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(), eq(relativePath), any())).thenReturn("uploads/image/avatar.png");
        when(storageService.getProvider()).thenReturn("local");
        when(serverAddressProvider.getServerAddress()).thenReturn("https://server.test");

        mockMvc.perform(multipart("/api/file/upload").file(file).param("type", "image"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("上传成功"))
            .andExpect(jsonPath("$.data.relativePath").value("uploads/image/avatar.png"))
            .andExpect(jsonPath("$.data.url").value("https://server.test/uploads/image/avatar.png"));
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
        String relativePath = "image/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(), eq(relativePath), any())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/file/upload").file(file).param("type", "image"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("文件上传失败，请稍后重试"));
    }
}
