package com.xiaozhi.communication.protocol;

import com.xiaozhi.enums.ListenMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发正确性压测：多台设备同时反复跑完整生命周期，钉住并发下的状态清理。
 *
 * <p>其余协议用例都是单设备顺序剧本，钉不住竞态；而会话注册表、设备索引、VAD/AEC 的按会话状态
 * 分散在四个不同的关闭路径上（连接回调、主动 goodbye、超时、退出意图），任何一条漏掉清理，
 * 单设备用例照样全绿，只有并发反复开合才会把残留堆出来。
 *
 * <p>本类只断言终态与异常，不断言时序：并发下帧序、识别流序号都不确定，
 * 因此用 {@link FakeDevice#sendFrames} 而不是会用全局 STT 计数做同步的 {@link FakeDevice#speak}。
 */
class ConcurrentSessionTortureTest {

    private static final int DEVICE_COUNT = 8;
    private static final int ROUNDS_PER_DEVICE = 6;
    private static final int RECONNECT_THREADS = 4;
    private static final int RECONNECT_ROUNDS = 8;
    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(10);
    private static final String SHARED_DEVICE_ID = "94:a9:90:2b:dd:00";

    private ProtocolTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = ProtocolTestHarness.create();
    }

    @AfterEach
    void tearDown() {
        harness.shutdown();
    }

    @Test
    void concurrentDeviceLifecyclesLeaveNoSessionOrIndexEntryBehind() {
        List<String> deviceIds = registerDevices(DEVICE_COUNT);

        List<Throwable> failures = runConcurrently(deviceIds.stream()
                .map(deviceId -> (Runnable) () -> {
                    for (int round = 0; round < ROUNDS_PER_DEVICE; round++) {
                        runFullLifecycle(deviceId);
                    }
                })
                .toList());

        assertThat(failures).isEmpty();
        awaitQuiescent();
    }

    @Test
    void concurrentReconnectsOfOneDeviceLeaveNoSessionOrIndexEntryBehind() {
        harness.withBoundDevice(SHARED_DEVICE_ID, 1);

        List<Runnable> workers = new ArrayList<>();
        for (int i = 0; i < RECONNECT_THREADS; i++) {
            workers.add(() -> {
                for (int round = 0; round < RECONNECT_ROUNDS; round++) {
                    FakeDevice device = harness.connect(SHARED_DEVICE_ID);
                    device.hello();
                    // 索引每台设备只留一条，同设备并发重连时别的线程会合法地覆盖或摘除，
                    // 过程中不可断言，只断言终态；非空真由上一条用例的哨兵保证
                    device.disconnect();
                }
            });
        }

        List<Throwable> failures = runConcurrently(workers);

        assertThat(failures).isEmpty();
        awaitQuiescent();
    }

    /** 每条关闭路径都必须释放按会话持有的 VAD 与 AEC 状态，否则按会话数持续泄漏 */
    @Test
    void concurrentDeviceLifecyclesResetVadAndAecForEverySession() {
        List<String> deviceIds = registerDevices(DEVICE_COUNT);
        List<String> sessionIds = new CopyOnWriteArrayList<>();

        List<Throwable> failures = runConcurrently(deviceIds.stream()
                .map(deviceId -> (Runnable) () -> {
                    for (int round = 0; round < ROUNDS_PER_DEVICE; round++) {
                        sessionIds.add(runFullLifecycle(deviceId));
                    }
                })
                .toList());

        assertThat(failures).isEmpty();
        awaitQuiescent();
        assertThat(sessionIds).hasSize(DEVICE_COUNT * ROUNDS_PER_DEVICE);
        for (String sessionId : sessionIds) {
            assertThat(harness.vad().isSessionInitialized(sessionId))
                .as("会话 %s 的 VAD 状态未释放", sessionId)
                .isFalse();
        }
        assertThat(harness.aec().resetCalls()).containsAll(sessionIds);
    }

    /** 连接 → 握手 → 收一句 → 打断 → 断链，返回本轮的 sessionId */
    private String runFullLifecycle(String deviceId) {
        FakeDevice device = harness.connect(deviceId);
        device.hello();
        // 哨兵：索引确实被写过，终态的「已清空」断言才不是空真
        assertThat(harness.deviceSessionIndex()).containsKey(deviceId);
        device.listenStart(ListenMode.AUTO);
        device.sendFrames(ScriptedVadService.SPEECH_START,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_END);
        device.abort("wake_word_detected");
        device.disconnect();
        return device.sessionId();
    }

    /** 等异步收尾跑完后断言终态：会话表与设备索引都不能有残留 */
    private void awaitQuiescent() {
        AwaitHelper.until("全部会话已摘除", SETTLE_TIMEOUT,
                () -> harness.sessionManager().getAllSessions().isEmpty());
        AwaitHelper.until("设备索引已清空", SETTLE_TIMEOUT,
                () -> harness.deviceSessionIndex().isEmpty());
    }

    /** 同时起跑所有 worker，收集各线程抛出的异常 */
    private static List<Throwable> runConcurrently(List<Runnable> workers) {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(workers.size())) {
            for (Runnable worker : workers) {
                pool.execute(() -> {
                    try {
                        startGate.await();
                        worker.run();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
            }
            startGate.countDown();
        }
        return failures;
    }

    private List<String> registerDevices(int count) {
        List<String> deviceIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String deviceId = String.format("94:a9:90:2b:dd:%02d", i + 1);
            // 先登记好设备档案，避免并发 connect 时才去补档
            harness.withBoundDevice(deviceId, 1);
            deviceIds.add(deviceId);
        }
        return deviceIds;
    }
}
