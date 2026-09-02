package com.xiaozhi.communication.controller;

import com.xiaozhi.ai.llm.service.VisionService;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.auth.DeviceAuthService;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住视觉问答接口的鉴权链路：token 必须是 DeviceAuthService 签发的 vision token，
 * 且 session 存活、设备与 session 匹配、图片可解析，四道关卡各自的拒绝原因都要能区分开。
 * <p>
 * ControllerTestSupport 依赖 xiaozhi-server 的 GlobalExceptionHandler，所以本测试留在 server 模块。
 */
@ExtendWith(MockitoExtension.class)
class VLChatControllerTest extends ControllerTestSupport {

    private static final String DEVICE_ID = "aa:bb:cc:dd:ee:ff";

    @Mock
    private VisionService visionService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ChatSession chatSession;

    private DeviceAuthService deviceAuthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deviceAuthService = new DeviceAuthService();
        ReflectionTestUtils.setField(deviceAuthService, "secret", "test-secret");
        ReflectionTestUtils.setField(deviceAuthService, "expireSeconds", 3600L);
        ReflectionTestUtils.setField(deviceAuthService, "allowedDevicesConfig", "");
        ReflectionTestUtils.invokeMethod(deviceAuthService, "init");

        VLChatController controller = new VLChatController();
        ReflectionTestUtils.setField(controller, "visionService", visionService);
        ReflectionTestUtils.setField(controller, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(controller, "deviceAuthService", deviceAuthService);
        ReflectionTestUtils.setField(controller, "imageValidator", new ImageValidator());
        mockMvc = buildMockMvc(controller);

        DeviceBO device = new DeviceBO();
        device.setDeviceId(DEVICE_ID);
        lenient().when(chatSession.getDevice()).thenReturn(device);
        lenient().when(sessionManager.getSession("session-1")).thenReturn(chatSession);
    }

    private MockMultipartFile jpegFile() {
        // 最小可解析 JPEG 由 ImageValidatorTest 覆盖；此处用真实编码图
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpg", out);
            return new MockMultipartFile("file", "photo.jpg", "image/jpeg", out.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void acceptsValidVisionToken() throws Exception {
        when(visionService.recognize(any(), any())).thenReturn("一只猫");
        String token = deviceAuthService.generateVisionToken("session-1", DEVICE_ID);

        mockMvc.perform(multipart("/api/vl/chat")
                        .file(jpegFile())
                        .param("question", "这是什么")
                        .header("authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.text").value("一只猫"));
    }

    @Test
    void rejectsRawSessionIdAsToken() throws Exception {
        mockMvc.perform(multipart("/api/vl/chat")
                        .file(jpegFile())
                        .param("question", "这是什么")
                        .header("authorization", "Bearer session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("认证信息无效或已过期"));
    }

    @Test
    void rejectsMissingAuthorization() throws Exception {
        mockMvc.perform(multipart("/api/vl/chat")
                        .file(jpegFile())
                        .param("question", "这是什么"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("缺少认证信息或格式错误"));
    }

    @Test
    void rejectsTokenOfDeadSession() throws Exception {
        String token = deviceAuthService.generateVisionToken("gone-session", DEVICE_ID);

        mockMvc.perform(multipart("/api/vl/chat")
                        .file(jpegFile())
                        .param("question", "这是什么")
                        .header("authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("session不存在"));
    }

    @Test
    void rejectsTokenDeviceMismatch() throws Exception {
        String token = deviceAuthService.generateVisionToken("session-1", "11:22:33:44:55:66");

        mockMvc.perform(multipart("/api/vl/chat")
                        .file(jpegFile())
                        .param("question", "这是什么")
                        .header("authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("设备与会话不匹配"));
    }

    @Test
    void rejectsInvalidImage() throws Exception {
        String token = deviceAuthService.generateVisionToken("session-1", DEVICE_ID);
        MockMultipartFile bad = new MockMultipartFile("file", "a.jpg", "image/jpeg", "not an image".getBytes());

        mockMvc.perform(multipart("/api/vl/chat")
                        .file(bad)
                        .param("question", "这是什么")
                        .header("authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("不支持的图片格式"));
    }
}
