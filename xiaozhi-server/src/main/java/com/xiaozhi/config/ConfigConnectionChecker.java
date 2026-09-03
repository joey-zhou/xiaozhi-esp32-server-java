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
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * 配置试拨：拿一份配置去 Provider 发起一次真实调用，把调用结果与报错翻译成给前端看的响应。
 * <p>只读，不落库、不改配置，也不向下游共享缓存留下任何临时凭据。
 */
@Slf4j
@Service
public class ConfigConnectionChecker {

    /**
     * 测试用临时配置 ID 的取号器，取值落在负数区间，与真实配置的正数 ID 不重合。
     * <p>硬约束：每次测试都要拿到独立的 ID。下游按 configId 键控 Token 与连接缓存，
     * 共用一个固定值会让不同用户的临时凭据落进同一个缓存槽。
     */
    private static final AtomicInteger TRANSIENT_CONFIG_ID_SEQ = new AtomicInteger();

    /** 送帧间隔，与样本音频每帧 60ms 的时长一致 */
    private static final Duration FRAME_INTERVAL = Duration.ofMillis(60);

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private SttServiceFactory sttServiceFactory;

    @Resource
    private TokenResolver tokenResolver;

    /**
     * 测试配置：使用表单当前值（可能未保存）直接发起一次真实调用，将 Provider 报错原样返回给前端。
     * <p>硬约束：库里保存的密钥只能配库里保存的端点使用。本人的配置允许用表单值覆盖端点，
     * 共享配置一律按库里那条原样测试，表单里的端点与密钥都不生效。
     */
    public ApiResponse<Void> test(ConfigTestReq req, Integer userId) {
        String configType = req.getConfigType();
        if (!"llm".equals(configType) && !"stt".equals(configType)) {
            return ApiResponse.error("暂不支持测试该类型配置");
        }
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null
                ? null
                : configRepository.findById(form.getConfigId()).orElse(null);
        ConfigBO bo = resolveConfigUnderTest(saved, form, userId);
        try {
            if ("stt".equals(configType)) {
                return testStt(bo);
            }
            if (ConfigBO.ModelType.embedding.getValue().equals(bo.getModelType())) {
                var embeddingModel = chatModelFactory.getEmbeddingModel(bo);
                float[] vector = embeddingModel.embed("测试");
                return ApiResponse.success("连接成功，返回向量维度：" + vector.length);
            }
            if (ConfigBO.ModelType.vision.getValue().equals(bo.getModelType())) {
                return testVision(bo);
            }
            ChatModel chatModel = chatModelFactory.createChatModel(bo, new RoleBO());
            ChatResponse response = chatModel.call(new Prompt(new UserMessage("你好，这是一次配置测试，请用一句话回复。")));
            String reply = response.getResult().getOutput().getText();
            return ApiResponse.success("连接成功，模型返回：" + reply);
        } catch (Exception e) {
            log.warn("配置测试失败 - provider: {}, model: {}", bo.getProvider(), bo.getConfigName(), e);
            return ApiResponse.error(extractErrorMessage(e));
        }
    }

    /**
     * 定出本次真正拿去外呼的配置。
     * 未保存的表单整条用表单值；本人已保存的配置用表单值覆盖、缺的补库里；
     * 他人共享的配置整条用库里的，表单不参与，避免已保存的密钥被配上调用方指定的端点。
     */
    private ConfigBO resolveConfigUnderTest(AiConfig saved, ConfigBO form, Integer userId) {
        if (saved == null) {
            return form;
        }
        if (Objects.equals(saved.getUserId(), userId)) {
            return saved.mergePatch(form);
        }
        return saved.mergePatch(new ConfigBO());
    }

    /**
     * 语音识别测试：把一段内嵌的 16k 单声道 PCM 当作一轮语音喂给识别服务，返回识别文本。
     */
    private ApiResponse<Void> testStt(ConfigBO bo) {
        // 表单里的临时凭据不能进 provider 缓存，configId 也不能带真实值去动线上连接与 Token 缓存
        bo.setConfigId(nextTransientConfigId());
        SttResult result;
        try {
            SttService sttService = sttServiceFactory.createTransientSttService(bo);
            // 按设备上行的节奏送帧，识别服务按实时流限速时才不会拒收
            result = sttService.stream(
                    Flux.fromIterable(ConfigTestSamples.speechFrames()).delayElements(FRAME_INTERVAL));
        } finally {
            // 临时凭据换来的连接与 Token 用完即弃，不留在按 configId 键控的共享缓存里
            sttServiceFactory.removeCache(bo);
            tokenResolver.removeCache(bo);
        }
        if (result == null) {
            return ApiResponse.error("识别服务未启动，请检查配置项是否填写完整");
        }
        if (result.operationFailed()) {
            return ApiResponse.error(sttFailureMessage(result.failureReason()));
        }
        if (!StringUtils.hasText(result.text())) {
            return ApiResponse.error("连接正常但没有识别出文本，请确认该模型支持 16kHz 中文语音");
        }
        return ApiResponse.success(
                "识别成功，测试语音说的是「" + ConfigTestSamples.SPEECH_TEXT + "」，识别结果：" + result.text());
    }

    private static int nextTransientConfigId() {
        return Integer.MIN_VALUE + (TRANSIENT_CONFIG_ID_SEQ.getAndIncrement() & Integer.MAX_VALUE);
    }

    private String sttFailureMessage(String failureReason) {
        return switch (failureReason) {
            case SttResult.FAILURE_UPSTREAM_ERROR -> "识别服务返回错误，请检查密钥、接口地址与网络";
            case SttResult.FAILURE_TIMEOUT -> "等待识别结果超时，请检查网络与服务可用性";
            default -> "本地识别处理异常，详情见服务端日志";
        };
    }

    /**
     * 视觉模型测试：带一张内嵌图片提问，验证图片通道而非只验证文本通道。
     */
    private ApiResponse<Void> testVision(ConfigBO bo) {
        ChatModel chatModel = chatModelFactory.createChatModel(bo, new RoleBO());
        Media image = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(ConfigTestSamples.imagePng())
                .build();
        UserMessage message = UserMessage.builder()
                .media(image)
                .text(ConfigTestSamples.IMAGE_QUESTION)
                .build();
        ChatResponse response = chatModel.call(new Prompt(message));
        String reply = response.getResult().getOutput().getText();
        return ApiResponse.success("连接成功，测试图片是白底红色实心圆，模型返回：" + reply);
    }

    /** 取 Provider 返回的原始报错体作为提示，取不到再退回异常自身的 message */
    private String extractErrorMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof RestClientResponseException rcre) {
                String body = rcre.getResponseBodyAsString();
                return StringUtils.hasText(body) ? body : rcre.getMessage();
            }
            t = t.getCause();
        }
        return StringUtils.hasText(e.getMessage()) ? e.getMessage() : "连接测试失败";
    }
}
