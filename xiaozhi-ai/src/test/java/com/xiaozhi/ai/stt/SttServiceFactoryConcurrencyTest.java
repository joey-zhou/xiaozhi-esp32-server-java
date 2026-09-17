package com.xiaozhi.ai.stt;

import com.xiaozhi.common.model.bo.ConfigBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同一个 provider:configId 在并发建会话时只能产出一个 SttService 实例。
 * <p>
 * 建实例会连带初始化第三方连接、换取访问 Token，重复创建等于对同一份凭据重复取号；
 * 之前这里是 containsKey 再 put 的 check-then-act，现在靠 computeIfAbsent 的原子性保证。
 * 这条性质没有测试钉着，改回两步写法在单线程下完全看不出来。
 */
class SttServiceFactoryConcurrencyTest {

    private SttServiceFactory factory;
    private final ConcurrentLinkedQueue<ConfigBO> creations = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() {
        // createApiService 里除了 switch 建实例还会包一层监控代理；这里只关心「建了几次」，
        // 用一个记账用的子类替掉创建过程，避免真的去连第三方
        factory = new SttServiceFactory() {
            @Override
            public SttService createApiService(ConfigBO config) {
                creations.add(config);
                return new RecordingSttService();
            }
        };
    }

    @Test
    void concurrentSessionsShareOneInstancePerConfig() throws Exception {
        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<SttService> resolved = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<>()));

        IntStream.range(0, threads).forEach(i -> Thread.startVirtualThread(() -> {
            try {
                start.await();
                resolved.add(factory.getSttService(new ConfigBO().setProvider("aliyun").setConfigId(9)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }));

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(creations)
            .as("同一 provider:configId 重复建实例，等于对同一份第三方凭据重复初始化连接/取 Token")
            .hasSize(1);
        assertThat(resolved)
            .as("并发调用方拿到的必须是同一个实例")
            .hasSize(1);
    }

    @Test
    void differentConfigIdsGetTheirOwnInstance() {
        SttService first = factory.getSttService(new ConfigBO().setProvider("aliyun").setConfigId(1));
        SttService second = factory.getSttService(new ConfigBO().setProvider("aliyun").setConfigId(2));

        assertThat(first)
            .as("不同配置必须各建各的，否则一个用户的第三方凭据会被另一个用户的会话借走")
            .isNotSameAs(second);
        assertThat(creations).hasSize(2);
    }

    private static class RecordingSttService implements SttService {
        @Override
        public String getProviderName() {
            return "recording";
        }

        @Override
        public SttResult stream(Flux<byte[]> audioSink) {
            return null;
        }
    }
}
