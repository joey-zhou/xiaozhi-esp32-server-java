package com.xiaozhi.ai.tts;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.xiaozhi.ai.tts.providers.SherpaOnnxTtsService;
import com.xiaozhi.common.model.bo.ConfigBO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 钉住配置变更时对两级缓存的处理：服务实例照常清除，sherpa-onnx 的本地模型实例必须原样保留。
 * 释放模型会 delete native 指针并置空，与正在执行的合成并发即 use-after-free，
 * 运维改一次 TTS 配置就能让整个节点 SIGSEGV 掉线。
 */
class TtsServiceFactoryModelCacheTest {

    /** 模型缓存键格式：模型目录绝对路径 + ":" + 模型类型 */
    private static final String MODEL_CACHE_KEY = "/opt/xiaozhi/models/tts/vits-melo-tts-zh_en:vits";

    /** 服务实例缓存键格式：provider:configId:voiceName:pitch:speed:instruction，voiceName 自身含冒号 */
    private static final String SHERPA_SERVICE_KEY = "sherpa-onnx:7:vits-melo-tts-zh_en:vits:0:1.0:1.0:null";
    private static final String EDGE_SERVICE_KEY = "edge:8:zh-CN-XiaoyiNeural:1.0:1.0:null";

    @AfterEach
    void clearSharedModelCache() {
        sherpaModelCache().remove(MODEL_CACHE_KEY);
    }

    @Test
    void removeCacheKeepsSherpaModelInstanceAlive() {
        OfflineTts model = mock(OfflineTts.class);
        Map<String, OfflineTts> modelCache = sherpaModelCache();
        modelCache.put(MODEL_CACHE_KEY, model);

        new TtsServiceFactory().removeCache(sherpaConfig());

        assertThat(modelCache).containsEntry(MODEL_CACHE_KEY, model);
        verify(model, never()).release();
    }

    @Test
    void removeCacheEvictsOnlyMatchingProviderServiceInstances() {
        TtsServiceFactory factory = new TtsServiceFactory();
        Map<String, TtsService> serviceCache = serviceCache(factory);
        serviceCache.put(SHERPA_SERVICE_KEY, mock(TtsService.class));
        serviceCache.put(EDGE_SERVICE_KEY, mock(TtsService.class));

        factory.removeCache(sherpaConfig());

        assertThat(serviceCache).containsOnlyKeys(EDGE_SERVICE_KEY);
    }

    private static ConfigBO sherpaConfig() {
        return new ConfigBO().setConfigId(7).setConfigType("tts").setProvider("sherpa-onnx");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, OfflineTts> sherpaModelCache() {
        return (Map<String, OfflineTts>) ReflectionTestUtils.getField(SherpaOnnxTtsService.class, "ttsCache");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TtsService> serviceCache(TtsServiceFactory factory) {
        return (Map<String, TtsService>) ReflectionTestUtils.getField(factory, "serviceCache");
    }
}
