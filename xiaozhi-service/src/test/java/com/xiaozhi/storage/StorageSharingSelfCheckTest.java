package com.xiaozhi.storage;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.common.InstanceIdHolder;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 本地存储下 server 与 dialogue 看不到同一份文件时，问题要在启动阶段暴露，而不是等用户点开历史消息。
 * 这里钉住判定的两个方向与全部放行分支：判定只针对登记了探针的存活实例，
 * 拿不到存储配置或 Redis 时一律放行——宁可漏判，也不能因为基础设施抖动把所有实例拦停。
 */
@ExtendWith(MockitoExtension.class)
class StorageSharingSelfCheckTest {

    private static final String REGISTRY_KEY = "xiaozhi:storage:probes";
    private static final String HEARTBEAT_KEY_PREFIX = "xiaozhi:storage:probe:heartbeat:";
    private static final String SELF_ID = "server-1";
    private static final String PEER_ID = "dialogue-1";
    private static final String PEER_PROBE_PATH = "/opt/dialogue/audio/.probe/dialogue-1";

    @TempDir
    private Path audioDir;

    @Mock
    private StorageServiceFactory storageServiceFactory;

    @Mock
    private StorageService storageService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StorageSharingSelfCheck selfCheck;

    @BeforeEach
    void setUp() {
        RuntimePathConfig runtimePathConfig = new RuntimePathConfig();
        runtimePathConfig.setAudioDir(audioDir.toString());

        selfCheck = new StorageSharingSelfCheck();
        ReflectionTestUtils.setField(selfCheck, "enabled", true);
        ReflectionTestUtils.setField(selfCheck, "storageServiceFactory", storageServiceFactory);
        ReflectionTestUtils.setField(selfCheck, "runtimePathConfig", runtimePathConfig);
        ReflectionTestUtils.setField(selfCheck, "instanceIdHolder", new InstanceIdHolder(SELF_ID));
        ReflectionTestUtils.setField(selfCheck, "stringRedisTemplate", stringRedisTemplate);
    }

    @AfterEach
    void release() {
        selfCheck.release();
    }

    @Test
    void passesWhenPeerProbeIsVisibleInLocalAudioDir() throws IOException {
        givenLocalStorage();
        givenRegistry(Map.of(selfProbeId(), selfProbe().toString(), PEER_ID, PEER_PROBE_PATH));
        givenPeerAlive();
        Files.createDirectories(audioDir.resolve(".probe"));
        Files.writeString(audioDir.resolve(".probe").resolve(PEER_ID), "instanceId=" + PEER_ID);

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();

        // 判定必须在自己的探针写好之后进行，否则先启动的实例永远看不到后启动实例的探针
        assertThat(selfProbe()).exists();
        assertThat(Files.readString(selfProbe())).contains("probeId=" + selfProbeId()).contains("writtenAt=");
        verify(hashOperations).put(REGISTRY_KEY, selfProbeId(), selfProbe().toString());
        verify(valueOperations).set(eq(HEARTBEAT_KEY_PREFIX + selfProbeId()), eq("1"), anyLong(), any());
    }

    @Test
    void refusesToStartWhenPeerProbeIsInvisible() {
        givenLocalStorage();
        givenRegistry(Map.of(selfProbeId(), selfProbe().toString(), PEER_ID, PEER_PROBE_PATH));
        givenPeerAlive();

        assertThatThrownBy(() -> selfCheck.verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PEER_ID)
                .hasMessageContaining(audioDir.resolve(".probe").resolve(PEER_ID).toString())
                .hasMessageContaining(PEER_PROBE_PATH)
                .hasMessageContaining("xiaozhi.runtime.audio-dir")
                .hasMessageContaining("kill -9");
    }

    @Test
    void passesWhenRedisIsUnreachable() {
        givenLocalStorage();
        when(stringRedisTemplate.<Object, Object>opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries(REGISTRY_KEY)).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();
    }

    @Test
    void skipsInstancesThatHaveNotRegisteredAProbe() {
        // 滚动升级期间的老版本实例不写探针也不登记，找不到它的探针属于预期，不能据此拦停新实例
        givenLocalStorage();
        givenRegistry(Map.of(selfProbeId(), selfProbe().toString()));

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();

        verify(valueOperations, never()).multiGet(any());
    }

    @Test
    void cleansExpiredRegistrationAndLeavesItOutOfTheCheck() {
        givenLocalStorage();
        givenRegistry(Map.of(selfProbeId(), selfProbe().toString(), PEER_ID, PEER_PROBE_PATH));
        when(valueOperations.multiGet(List.of(HEARTBEAT_KEY_PREFIX + PEER_ID)))
                .thenReturn(Collections.singletonList(null));

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();

        verify(hashOperations).delete(REGISTRY_KEY, PEER_ID);
    }

    @Test
    void skipsEntirelyOnCloudStorage() {
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.getProvider()).thenReturn("tencent");

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();

        verifyNoInteractions(stringRedisTemplate);
        assertThat(audioDir.resolve(".probe")).doesNotExist();
    }

    @Test
    void skipsWhenStorageConfigCannotBeRead() {
        when(storageServiceFactory.getStorageService()).thenThrow(new IllegalStateException("config unavailable"));

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void skipsWhenSelfCheckIsDisabled() {
        ReflectionTestUtils.setField(selfCheck, "enabled", false);

        assertThatCode(() -> selfCheck.verify()).doesNotThrowAnyException();

        verifyNoInteractions(storageServiceFactory, stringRedisTemplate);
    }

    private void givenLocalStorage() {
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.getProvider()).thenReturn("local");
    }

    private void givenRegistry(Map<String, String> registry) {
        when(stringRedisTemplate.<Object, Object>opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries(REGISTRY_KEY)).thenReturn(new LinkedHashMap<Object, Object>(registry));
    }

    private void givenPeerAlive() {
        when(valueOperations.multiGet(List.of(HEARTBEAT_KEY_PREFIX + PEER_ID))).thenReturn(List.of("1"));
    }

    /** 登记身份带进程号，同机同 hostname 的两个进程才区分得开 */
    private static String selfProbeId() {
        return SELF_ID + "#" + ProcessHandle.current().pid();
    }

    private Path selfProbe() {
        return audioDir.resolve(".probe").resolve(selfProbeId().replaceAll("[^A-Za-z0-9._-]", "_"));
    }
}
