package com.xiaozhi.ai.stt;

import com.xiaozhi.event.AiConfigChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 本进程改了识别配置（server 的试听、配置试拨都在这里建实例），缓存的旧实例要跟着清 */
class SttServiceFactoryConfigChangeTest {

    @Test
    void sttConfigChangeEvictsThatConfigsInstanceOnly() {
        SttServiceFactory factory = new SttServiceFactory();
        Map<String, SttService> cache = serviceCache(factory);
        cache.put("aliyun:7", mock(SttService.class));
        cache.put("funasr:8", mock(SttService.class));

        factory.onAiConfigChanged(new AiConfigChangedEvent(this, "stt", 7));
        factory.onAiConfigChanged(new AiConfigChangedEvent(this, "tts", 8));

        assertThat(cache).containsOnlyKeys("funasr:8");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SttService> serviceCache(SttServiceFactory factory) {
        return (Map<String, SttService>) ReflectionTestUtils.getField(factory, "serviceCache");
    }
}
