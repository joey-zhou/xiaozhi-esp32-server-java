package com.xiaozhi.communication.common;

import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨实例控制面的 JSON 载荷契约：发送端 {@link RedisBroadcast} 写的字段，
 * 必须正好是接收端 {@link RedisSubscriber} 读的字段。
 * <p>
 * 这两端分处 xiaozhi-common 与 xiaozhi-dialogue，靠字符串 key 对齐，编译器一个字都拦不住。
 * 改个字段名、把 boolean 写成字符串，本地全绿、只有跨实例部署才会暴露。所以这里把发送端真实产出的报文
 * 直接喂给接收端，两边同时改才算通过。
 */
@ExtendWith(MockitoExtension.class)
class CrossInstanceContractTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DeviceRegistry deviceRegistry;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private ConfigService configService;

    private RedisBroadcast broadcast;
    private RedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        broadcast = new RedisBroadcast();
        ReflectionTestUtils.setField(broadcast, "stringRedisTemplate", stringRedisTemplate);

        subscriber = new RedisSubscriber();
        ReflectionTestUtils.setField(subscriber, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(subscriber, "deviceRegistry", deviceRegistry);
        ReflectionTestUtils.setField(subscriber, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(subscriber, "storageServiceFactory", storageServiceFactory);
        ReflectionTestUtils.setField(subscriber, "configService", configService);
    }

    /** 取发送端这一次真正 publish 出去的报文，不是测试自己拼的字符串 */
    private String publishedOn(String channel) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(stringRedisTemplate).convertAndSend(eq(channel), payload.capture());
        return (String) payload.getValue();
    }

    @Test
    void closeSessionCarriesTheExcludedSessionId() {
        broadcast.closeDeviceSession("dev-1", "session-new");
        String message = publishedOn(RedisBroadcast.CHANNEL_CLOSE_SESSION);

        ChatSession existing = session("session-new");
        when(sessionManager.getSessionByDeviceId("dev-1")).thenReturn(existing);
        subscriber.onCloseSession(message);

        verify(sessionManager, never()).closeSession(existing);
    }

    @Test
    void closeSessionClosesWhenTheSessionIsNotTheExcludedOne() {
        broadcast.closeDeviceSession("dev-1", "session-new");
        String message = publishedOn(RedisBroadcast.CHANNEL_CLOSE_SESSION);

        ChatSession stale = session("session-old");
        when(sessionManager.getSessionByDeviceId("dev-1")).thenReturn(stale);
        subscriber.onCloseSession(message);

        verify(sessionManager).closeSession(stale);
    }

    @Test
    void ossConfigChangeRefreshesTheStorageFactory() {
        broadcast.configChanged("oss", 12);
        String message = publishedOn(RedisBroadcast.CHANNEL_CONFIG_CHANGED);

        subscriber.onConfigChanged(message);

        verify(storageServiceFactory).refresh();
    }

    @Test
    void configChangeLooksUpTheConfigIdItWasGiven() {
        broadcast.configChanged("stt", 34);
        String message = publishedOn(RedisBroadcast.CHANNEL_CONFIG_CHANGED);

        subscriber.onConfigChanged(message);

        verify(configService).getBO(34);
    }

    private static ChatSession session(String sessionId) {
        return new ChatSession(sessionId) {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isAudioChannelOpen() {
                return true;
            }

            @Override
            public void close() {
            }

            @Override
            public void sendTextMessage(String message) {
            }

            @Override
            public void sendBinaryMessage(byte[] message, long timestamp) {
            }
        };
    }
}
