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

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
     * <p>每次测试都要拿到独立的 ID。下游按 configId 键控 Token 与连接缓存，
     * 共用一个固定值会让不同用户的临时凭据落进同一个缓存槽。
     */
    private static final AtomicInteger TRANSIENT_CONFIG_ID_SEQ = new AtomicInteger();

    /** 送帧间隔，与样本音频每帧 60ms 的时长一致 */
    private static final Duration FRAME_INTERVAL = Duration.ofMillis(60);

    /** 允许试拨的接口协议：HTTP 族给模型接口，WebSocket 族给流式识别服务 */
    private static final Set<String> SUPPORTED_ENDPOINT_SCHEMES = Set.of("http", "https", "ws", "wss");

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
     * 测试配置：使用表单当前值（可能未保存）直接发起一次真实调用，把结果翻译成给前端看的结论。
     * <p>库里保存的密钥只能配库里保存的端点使用。本人的配置允许用表单值覆盖端点，
     * 共享配置一律按库里那条原样测试，表单里的端点与密钥都不生效。
     * <p>接口地址由表单直接给，本进程能连到哪就能试到哪；私网地址是自建模型（Ollama、vLLM、MinIO 等）
     * 的正常用法，不能拦，因此只做协议白名单，并且不把下游报文回显给前端，避免退化成内网探测的读取通道。
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
        String unsupportedEndpoint = unsupportedEndpointMessage(bo.getApiUrl());
        if (unsupportedEndpoint != null) {
            return ApiResponse.error(unsupportedEndpoint);
        }
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
            String reply = extractReply(response);
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
        String reply = extractReply(response);
        return ApiResponse.success("连接成功，测试图片是白底红色实心圆，模型返回：" + reply);
    }

    /**
     * 部分自研 ChatModel 在上游报错时会把异常吞掉、返回空 generations 的 ChatResponse
     * （避免打断正在进行的对话），试拨场景没有正在进行的对话可保护，这里直接转成明确的报错文案
     */
    private String extractReply(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new EmptyModelResponseException("模型未返回有效结果，请检查密钥、接口地址与模型名称是否正确");
        }
        return response.getResult().getOutput().getText();
    }

    /** 消息内容是自己拼的，不含下游细节，可以直接展示给前端 */
    private static class EmptyModelResponseException extends RuntimeException {
        EmptyModelResponseException(String message) {
            super(message);
        }
    }

    /**
     * 接口地址只放行 HTTP 与 WebSocket 两族协议：模型服务就这两种接法（FunASR 一类的识别服务走 ws），
     * 其余协议（file、gopher 等）拿不到模型响应，只会变成读本地资源的通道。
     * 地址没填时按 Provider 的内置默认端点走，不在这里判。
     *
     * @return 不合规时的提示文案，合规返回 null
     */
    private String unsupportedEndpointMessage(String apiUrl) {
        if (!StringUtils.hasText(apiUrl)) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(apiUrl.trim());
        } catch (IllegalArgumentException e) {
            return "接口地址格式不正确";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ENDPOINT_SCHEMES.contains(scheme)) {
            return "接口地址只支持 http/https/ws/wss";
        }
        // 主机名不在这里挑剔：URI 的解析比实际能连的地址严（如带下划线的容器名），拦下来会误伤正常配置
        return null;
    }

    /**
     * Provider 的原始报错体可能带下游内部地址、账号线索甚至密钥片段，只按状态码给一句能判读的结论，
     * 原文留在服务端日志里给运维看。
     */
    private String extractErrorMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof RestClientResponseException rcre) {
                return upstreamFailureMessage(rcre.getStatusCode().value());
            }
            if (t instanceof EmptyModelResponseException) {
                return t.getMessage();
            }
            t = t.getCause();
        }
        return "连接测试失败，详情见服务端日志";
    }

    private String upstreamFailureMessage(int status) {
        if (status == 401 || status == 403) {
            return "接口返回未授权（" + status + "），请检查密钥与账号权限";
        }
        if (status == 404) {
            return "接口地址或模型名称不存在（404），请检查后重试";
        }
        if (status == 429) {
            return "接口返回限流（429），请稍后再试";
        }
        if (status >= 500) {
            return "接口返回服务端错误（" + status + "），请稍后再试或联系服务商";
        }
        return "接口返回错误（" + status + "），请检查接口地址、密钥与模型名称";
    }
}
