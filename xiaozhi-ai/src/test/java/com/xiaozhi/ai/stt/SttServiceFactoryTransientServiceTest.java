package com.xiaozhi.ai.stt;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉住临时配置的构造入口不碰缓存：配置测试用的是表单里还没保存的凭据，
 * 一旦按 provider:configId 落进缓存，后续真实会话就会拿这份临时凭据去识别。
 */
class SttServiceFactoryTransientServiceTest {

    @Test
    void transientServiceIsNotStoredInCache() {
        SttServiceFactory factory = newFactory();

        SttService service = factory.createTransientSttService(apiConfig());

        assertThat(service).isNotNull();
        assertThat(serviceCache(factory)).isEmpty();
    }

    @Test
    void transientServiceNeitherReusesNorReplacesCachedInstance() {
        SttServiceFactory factory = newFactory();
        SttService cached = factory.getSttService(apiConfig());

        SttService first = factory.createTransientSttService(apiConfig());
        SttService second = factory.createTransientSttService(apiConfig());

        assertThat(first).isNotSameAs(cached);
        assertThat(second).isNotSameAs(first);
        assertThat(factory.getSttService(apiConfig())).isSameAs(cached);
    }

    private static SttServiceFactory newFactory() {
        SttServiceFactory factory = new SttServiceFactory();
        ReflectionTestUtils.setField(factory, "tokenClient", mock(ProviderTokenClient.class));
        return factory;
    }

    private static ConfigBO apiConfig() {
        return new ConfigBO()
                .setConfigId(7)
                .setConfigType("stt")
                .setProvider("aliyun-nls")
                .setApiKey("form-key");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SttService> serviceCache(SttServiceFactory factory) {
        return (Map<String, SttService>) ReflectionTestUtils.getField(factory, "serviceCache");
    }
}
