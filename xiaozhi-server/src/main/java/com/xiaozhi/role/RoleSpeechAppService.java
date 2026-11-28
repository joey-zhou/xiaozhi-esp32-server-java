package com.xiaozhi.role;

import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.ai.tts.SherpaVoiceProbe;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.TestVoiceReq;
import com.xiaozhi.common.model.resp.LocalSttResp;
import com.xiaozhi.common.model.resp.SherpaVoiceResp;
import com.xiaozhi.common.model.resp.TestVoiceResp;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 角色页的语音能力：音色试听、本地识别与本地音色
 */
@Slf4j
@Service
public class RoleSpeechAppService {

    @Resource
    private ConfigService configService;
    @Resource
    private TtsServiceFactory ttsServiceFactory;
    @Resource
    private StorageServiceFactory storageServiceFactory;
    @Resource
    private RuntimePathConfig runtimePathConfig;
    @Resource
    private SttServiceFactory sttServiceFactory;
    @Resource
    private SherpaVoiceProbe sherpaVoiceProbe;

    /** 服务端启动时加载成功的本地识别，模型都没就位时 provider 为空 */
    public LocalSttResp localStt() {
        String provider = sttServiceFactory.getLocalDefaultProvider();
        return new LocalSttResp(provider, provider != null);
    }

    /** 本地 TTS 模型目录里能用的音色 */
    public List<SherpaVoiceResp> sherpaVoices() {
        return sherpaVoiceProbe.listVoices();
    }

    /** 按选的语音配置合成一句，存进对象存储给前端播 */
    public TestVoiceResp preview(TestVoiceReq req) {
        ConfigBO config = null;
        if (!TtsServiceFactory.DEFAULT_PROVIDER.equals(req.getProvider())) {
            if (req.getTtsId() == null) {
                throw new IllegalArgumentException("非 edge 提供方必须指定语音配置");
            }
            config = configService.getBO(req.getTtsId());
            if (config == null) {
                throw new ResourceNotFoundException("语音配置不存在或无权访问");
            }
        }

        Path audio;
        try {
            audio = ttsServiceFactory.getTtsService(config, req.getVoiceName(), req.getTtsPitch(), req.getTtsSpeed())
                    .textToSpeech(req.getMessage());
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("请先到语音合成配置页面配置对应Key", e);
        } catch (Exception e) {
            throw new OperationFailedException("测试语音合成失败", e);
        }
        if (audio == null) {
            throw new OperationFailedException("测试语音合成失败：未生成音频");
        }
        try {
            // 本地返回相对路径，云存储返回完整 URL；出参由响应切面统一签名
            String key = runtimePathConfig.getAudioDir() + audio.getFileName();
            return new TestVoiceResp(storageServiceFactory.getStorageService().upload(audio, key));
        } catch (Exception e) {
            throw new OperationFailedException("测试语音合成失败", e);
        }
    }
}
