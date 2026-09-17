package com.xiaozhi.ai.probe;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ProviderTokenClient;
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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住配置试拨的三条通道：语音配置走真实识别往返且不得带真实 configId 进下游缓存，
 * 视觉模型必须发图而不是发纯文本，其余聊天模型仍走纯文本；
 * 同时钉住协议白名单、上游报文不回显、空 generations 与内嵌样本本身的格式。
 */
@ExtendWith(MockitoExtension.class)
class ConfigProbeTest {

    private static final int CONFIG_ID = 7;

    /** 16kHz 单声道 16bit：每秒 32000 字节 */
    private static final double BYTES_PER_SECOND = 32000.0;

    private static final int MAX_FRAME_BYTES = 1920;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private SttServiceFactory sttServiceFactory;

    @Mock
    private ProviderTokenClient tokenClient;

    @Mock
    private SttService sttService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private ConfigProbe configProbe;

    @Test
    void sttTestFeedsSpeechSampleWithoutRealConfigId() {
        ConfigBO config = sttConfig();
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        ConfigProbeResultBO response = configProbe.probe(config);

        // probe 就地改写传入的 ConfigBO，测试用的临时 ID 落在负数区间
        assertThat(config.getConfigId()).isNegative();

        ArgumentCaptor<Flux<byte[]>> audioCaptor = ArgumentCaptor.captor();
        verify(sttService).stream(audioCaptor.capture());
        // 整段样本按 60ms 一帧限速送出，测试只取首帧确认送的是样本本身
        assertThat(audioCaptor.getValue().blockFirst()).isEqualTo(ConfigProbe.speechFrames().get(0));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("你好");
    }

    @Test
    void sttTestDropsTransientCredentialsFromSharedCaches() {
        ConfigBO config = sttConfig();
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        configProbe.probe(config);

        verify(sttServiceFactory).removeCache(config);
        verify(tokenClient).removeCache(config);
    }

    @Test
    void eachSttTestGetsItsOwnTransientConfigId() {
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly("你好"));

        ConfigBO first = sttConfig();
        ConfigBO second = sttConfig();
        configProbe.probe(first);
        configProbe.probe(second);

        assertThat(first.getConfigId()).isNegative();
        assertThat(second.getConfigId()).isNegative().isNotEqualTo(first.getConfigId());
    }

    @Test
    void sttTestReportsUpstreamFailure() {
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR));

        ConfigProbeResultBO response = configProbe.probe(sttConfig());

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("密钥");
    }

    @Test
    void sttTestRejectsEmptyRecognition() {
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(sttService);
        when(sttService.stream(any())).thenReturn(SttResult.textOnly(""));

        ConfigProbeResultBO response = configProbe.probe(sttConfig());

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("没有识别出文本");
    }

    /**
     * createTransientSttService 拿不到识别服务时，stream() 调用会在 finally 清缓存前抛出空指针，
     * 冒泡到外层统一 catch，落到兜底文案而不是把裸异常暴露给调用方。
     */
    @Test
    void nullTransientSttServiceProducesGenericFailureMessage() {
        when(sttServiceFactory.createTransientSttService(any(ConfigBO.class))).thenReturn(null);

        ConfigProbeResultBO response = configProbe.probe(sttConfig());

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("连接测试失败，详情见服务端日志");
        verify(sttServiceFactory).removeCache(any(ConfigBO.class));
        verify(tokenClient).removeCache(any(ConfigBO.class));
    }

    @Test
    void visionTestSendsImageAlongWithQuestion() {
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("图片里是一个红色的圆"));

        ConfigProbeResultBO response = configProbe.probe(llmConfig("vision"));

        List<Media> media = sentUserMessage().getMedia();
        assertThat(media).hasSize(1);
        assertThat(media.get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
        assertThat(media.get(0).getDataAsByteArray()).isEqualTo(ConfigProbe.imagePng());
        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("红色的圆");
    }

    @Test
    void chatTestSendsPlainTextOnly() {
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("你好"));

        ConfigProbeResultBO response = configProbe.probe(llmConfig("chat"));

        assertThat(sentUserMessage().getMedia()).isEmpty();
        assertThat(response.success()).isTrue();
    }

    @Test
    void embeddingTestReturnsVectorDimension() {
        when(chatModelFactory.getEmbeddingModel(any(ConfigBO.class))).thenReturn(embeddingModel);
        when(embeddingModel.embed("测试")).thenReturn(new float[] {0.1f, 0.2f, 0.3f});

        ConfigProbeResultBO response = configProbe.probe(llmConfig("embedding"));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("3");
    }

    /** 部分自研 ChatModel 上游报错时会吞异常、返回空 generations，须转成明确报错而不是让调用方读到空指针 */
    @Test
    void emptyGenerationsProducesExplicitFailure() {
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        ConfigProbeResultBO response = configProbe.probe(llmConfig("chat"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("模型未返回有效结果");
    }

    /** 下游报文可能带内部地址、账号线索甚至密钥片段，只按状态码给结论，原文不出前端 */
    @Test
    void upstreamResponseBodyIsNotEchoedToCaller() {
        when(chatModelFactory.createChatModel(any(ConfigBO.class), any(RoleBO.class))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new HttpClientErrorException(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                "{\"error\":{\"message\":\"invalid key sk-internal-secret\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        ConfigProbeResultBO response = configProbe.probe(llmConfig("chat"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).doesNotContain("sk-internal-secret");
        assertThat(response.message()).contains("401");
    }

    /** 试拨的接口地址由调用方直接给，HTTP 与 WebSocket 之外的协议一律不发起请求 */
    @Test
    void nonHttpEndpointIsRejectedBeforeDialing() {
        ConfigProbeResultBO response = configProbe.probe(llmConfig("chat").setApiUrl("file:///etc/passwd"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("接口地址只支持 http/https/ws/wss");
        verifyNoInteractions(chatModelFactory);
    }

    @Test
    void ttsConfigTypeIsStillRejected() {
        ConfigProbeResultBO response = configProbe.probe(
                new ConfigBO().setConfigId(CONFIG_ID).setConfigType("tts"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("暂不支持测试该类型配置");
        verifyNoInteractions(sttServiceFactory, chatModelFactory);
    }

    @Test
    void speechFramesArePcmChunksWithSilencePadding() {
        List<byte[]> frames = ConfigProbe.speechFrames();

        assertThat(frames).hasSizeGreaterThan(3);
        assertThat(frames).allSatisfy(frame -> {
            assertThat(frame.length).isBetween(2, MAX_FRAME_BYTES);
            assertThat(frame.length % 2).isZero();
        });
        assertThat(frames.get(0)).containsOnly((byte) 0);
        assertThat(frames.get(frames.size() - 1)).containsOnly((byte) 0);

        double seconds = totalBytes(frames) / BYTES_PER_SECOND;
        assertThat(seconds).isBetween(0.5, 2.0);
    }

    @Test
    void speechFramesCarryAudibleSpeech() {
        List<byte[]> frames = ConfigProbe.speechFrames();

        int peak = 0;
        for (byte[] frame : frames) {
            for (int i = 0; i + 1 < frame.length; i += 2) {
                int sample = (short) ((frame[i] & 0xFF) | (frame[i + 1] << 8));
                peak = Math.max(peak, Math.abs(sample));
            }
        }

        assertThat(peak).isBetween(16000, 32767);
    }

    @Test
    void speechFramesAreFreshCopiesPerCall() {
        List<byte[]> first = ConfigProbe.speechFrames();
        byte[] speechFrame = first.get(2);
        byte[] original = Arrays.copyOf(speechFrame, speechFrame.length);
        Arrays.fill(speechFrame, (byte) 0);

        List<byte[]> second = ConfigProbe.speechFrames();

        assertThat(second.get(2)).containsExactly(original);
    }

    @Test
    void imageSampleIsPng() {
        byte[] pngMagic = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        byte[] image = ConfigProbe.imagePng();

        assertThat(image).hasSizeBetween(pngMagic.length, 4096).startsWith(pngMagic);
    }

    private UserMessage sentUserMessage() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.captor();
        verify(chatModel).call(promptCaptor.capture());
        List<Message> instructions = promptCaptor.getValue().getInstructions();
        assertThat(instructions).hasSize(1);
        return (UserMessage) instructions.get(0);
    }

    private static int totalBytes(List<byte[]> frames) {
        return frames.stream().mapToInt(frame -> frame.length).sum();
    }

    private static ConfigBO sttConfig() {
        return new ConfigBO()
                .setConfigId(CONFIG_ID)
                .setConfigType("stt")
                .setProvider("aliyun-nls");
    }

    private static ConfigBO llmConfig(String modelType) {
        return new ConfigBO()
                .setConfigId(CONFIG_ID)
                .setConfigType("llm")
                .setModelType(modelType)
                .setProvider("openai")
                .setApiKey("form-key");
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
