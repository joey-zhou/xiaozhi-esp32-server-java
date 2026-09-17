package com.xiaozhi.config;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.port.TokenResolver;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住配置测试的三条通道：语音配置走真实识别往返且不得带真实 configId 进下游缓存，
 * 视觉模型必须发图而不是发纯文本，其余聊天模型仍走纯文本；
 * 同时钉住身份来自调用方、密钥回填只对当前用户本人的配置生效。
 */
@ExtendWith(MockitoExtension.class)
class ConfigConnectionCheckerTest {

    private static final int SAVED_CONFIG_ID = 7;
    private static final int CURRENT_USER_ID = 9;
    private static final int OTHER_USER_ID = 10;
    private static final int ADMIN_USER_ID = 1;

    @Mock
    private ConfigConvert configConvert;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private SttServiceFactory sttServiceFactory;

    @Mock
    private TokenResolver tokenResolver;

    @Mock
    private SttService sttService;

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ConfigConnectionChecker configConnectionChecker;

    @Test
    void sttTestFeedsSpeechSampleWithoutRealConfigId() {
        ConfigBO form = sttConfig().setApiKey(null);
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(form);
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(CURRENT_USER_ID)));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        ApiResponse<Void> response = configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        ConfigBO used = transientConfig();
        assertThat(used.getConfigId()).isNegative();
        assertThat(used.getApiKey()).isEqualTo("saved-key");

        ArgumentCaptor<Flux<byte[]>> audioCaptor = ArgumentCaptor.captor();
        verify(sttService).stream(audioCaptor.capture());
        // 整段样本按 60ms 一帧限速送出，测试只取首帧确认送的是样本本身
        assertThat(audioCaptor.getValue().blockFirst()).isEqualTo(ConfigTestSamples.speechFrames().get(0));

        assertThat(response.getCode()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(response.getMessage()).contains("你好");
    }

    @Test
    void sttTestDropsTransientCredentialsFromSharedCaches() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        verify(sttServiceFactory).removeCache(any(ConfigBO.class));
        verify(tokenResolver).removeCache(any(ConfigBO.class));
    }

    @Test
    void eachSttTestGetsItsOwnTransientConfigId() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenAnswer(invocation -> sttConfig().setConfigId(null));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        configConnectionChecker.test(request("stt"), CURRENT_USER_ID);
        configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        ArgumentCaptor<ConfigBO> configCaptor = ArgumentCaptor.captor();
        verify(sttServiceFactory, times(2)).createTransientSttService(configCaptor.capture());
        List<ConfigBO> used = configCaptor.getAllValues();
        assertThat(used.get(0).getConfigId()).isNegative();
        assertThat(used.get(1).getConfigId()).isNegative().isNotEqualTo(used.get(0).getConfigId());
    }

    @Test
    void callerUserIdIsCarriedIntoTheConfigUsedForTesting() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        assertThat(transientConfig().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    // 共享配置照常测得通，用的是库里那条的密钥与端点
    @Test
    void sharedConfigIsTestedWithItsOwnSavedCredentials() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig());
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(ADMIN_USER_ID)));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        ApiResponse<Void> response = configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        assertThat(response.getCode()).isEqualTo(ResultStatus.SUCCESS);
        ConfigBO used = transientConfig();
        assertThat(used.getApiKey()).isEqualTo("saved-key");
        assertThat(used.getApiUrl()).isEqualTo("https://saved.example.com");
        // 整条取库里那条，连归属与元数据都是库里的，表单一个字段都不参与
        assertThat(used.getUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(used.getConfigName()).isEqualTo("语音配置");
        assertThat(used.getState()).isEqualTo(AiConfig.STATE_ENABLED);
        assertThat(used.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_NO);
    }

    // 已保存的密钥只能配已保存的端点，表单里的端点对他人配置不生效
    @Test
    void formEndpointCannotBePairedWithAnotherUsersSavedSecret() {
        ConfigBO form = sttConfig().setApiKey("attacker-key").setApiUrl("https://attacker.example.com");
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(form);
        when(configRepository.findById(SAVED_CONFIG_ID)).thenReturn(Optional.of(savedSttConfig(OTHER_USER_ID)));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        ConfigBO used = transientConfig();
        assertThat(used.getApiUrl()).isEqualTo("https://saved.example.com");
        assertThat(used.getApiKey()).isEqualTo("saved-key");
    }

    @Test
    void sttTestReportsUpstreamFailure() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR));

        ApiResponse<Void> response = configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        assertThat(response.getCode()).isEqualTo(ResultStatus.ERROR);
        assertThat(response.getMessage()).contains("密钥");
    }

    @Test
    void sttTestRejectsEmptyRecognition() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(sttConfig().setConfigId(null));
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly(""));

        ApiResponse<Void> response = configConnectionChecker.test(request("stt"), CURRENT_USER_ID);

        assertThat(response.getCode()).isEqualTo(ResultStatus.ERROR);
        assertThat(response.getMessage()).contains("没有识别出文本");
    }

    @Test
    void visionTestSendsImageAlongWithQuestion() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(llmConfig("vision"));
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("图片里是一个红色的圆"));

        ApiResponse<Void> response = configConnectionChecker.test(request("llm"), CURRENT_USER_ID);

        List<Media> media = sentUserMessage().getMedia();
        assertThat(media).hasSize(1);
        assertThat(media.get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
        assertThat(media.get(0).getDataAsByteArray()).isEqualTo(ConfigTestSamples.imagePng());
        assertThat(response.getCode()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(response.getMessage()).contains("红色的圆");
    }

    @Test
    void chatTestSendsPlainTextOnly() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(llmConfig("chat"));
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("你好"));

        ApiResponse<Void> response = configConnectionChecker.test(request("llm"), CURRENT_USER_ID);

        assertThat(sentUserMessage().getMedia()).isEmpty();
        assertThat(response.getCode()).isEqualTo(ResultStatus.SUCCESS);
    }

    /** 下游报文可能带内部地址、账号线索甚至密钥片段，只按状态码给结论，原文不出前端 */
    @Test
    void upstreamResponseBodyIsNotEchoedToCaller() {
        when(configConvert.toBO(any(ConfigTestReq.class))).thenReturn(llmConfig("chat"));
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new HttpClientErrorException(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                "{\"error\":{\"message\":\"invalid key sk-internal-secret\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        ApiResponse<Void> response = configConnectionChecker.test(request("llm"), CURRENT_USER_ID);

        assertThat(response.getCode()).isEqualTo(ResultStatus.ERROR);
        assertThat(response.getMessage()).doesNotContain("sk-internal-secret");
        assertThat(response.getMessage()).contains("401");
    }

    /** 试拨的接口地址由表单直接给，HTTP 与 WebSocket 之外的协议一律不发起请求 */
    @Test
    void nonHttpEndpointIsRejectedBeforeDialing() {
        when(configConvert.toBO(any(ConfigTestReq.class)))
                .thenReturn(llmConfig("chat").setConfigId(null).setApiUrl("file:///etc/passwd"));

        ApiResponse<Void> response = configConnectionChecker.test(request("llm"), CURRENT_USER_ID);

        assertThat(response.getCode()).isEqualTo(ResultStatus.ERROR);
        assertThat(response.getMessage()).isEqualTo("接口地址只支持 http/https/ws/wss");
        verifyNoInteractions(chatModelFactory);
    }

    @Test
    void ttsConfigTypeIsStillRejected() {
        ApiResponse<Void> response = configConnectionChecker.test(request("tts"), CURRENT_USER_ID);

        assertThat(response.getCode()).isEqualTo(ResultStatus.ERROR);
        assertThat(response.getMessage()).isEqualTo("暂不支持测试该类型配置");
        verifyNoInteractions(sttServiceFactory, chatModelFactory);
    }

    private ConfigBO transientConfig() {
        ArgumentCaptor<ConfigBO> configCaptor = ArgumentCaptor.captor();
        verify(sttServiceFactory).createTransientSttService(configCaptor.capture());
        return configCaptor.getValue();
    }

    private UserMessage sentUserMessage() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.captor();
        verify(chatModel).call(promptCaptor.capture());
        List<Message> instructions = promptCaptor.getValue().getInstructions();
        assertThat(instructions).hasSize(1);
        return (UserMessage) instructions.get(0);
    }

    private static ConfigTestReq request(String configType) {
        ConfigTestReq req = new ConfigTestReq();
        req.setConfigType(configType);
        req.setConfigName("测试配置");
        req.setProvider("aliyun-nls");
        return req;
    }

    private static AiConfig savedSttConfig(int userId) {
        return AiConfig.reconstitute(SAVED_CONFIG_ID, userId, "stt", "aliyun-nls",
                "语音配置", null, null,
                null, "saved-key", null,
                null, null, "https://saved.example.com",
                null, AiConfig.STATE_ENABLED, false,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0));
    }

    private static ConfigBO sttConfig() {
        return new ConfigBO()
                .setConfigId(SAVED_CONFIG_ID)
                .setConfigType("stt")
                .setProvider("aliyun-nls");
    }

    private static ConfigBO llmConfig(String modelType) {
        return new ConfigBO()
                .setConfigId(SAVED_CONFIG_ID)
                .setConfigType("llm")
                .setModelType(modelType)
                .setProvider("openai")
                .setApiKey("form-key");
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
