package com.xiaozhi.communication.protocol;

import com.xiaozhi.dialogue.audio.AecService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 记流水的 AEC 假体，绕开 webrtc-java native（打包平台与运行平台不一致时真实 AEC 会整体降级，
 * 测试里不能依赖它）。
 *
 * <p>{@code process} 恒等返回麦克风数据，不做任何回声消除；其余方法只记调用流水。
 * 用例可断言的是"参考帧的调度契约"：下发一帧就 feedReference 一帧、timestamp 与下行帧一致、
 * abort 后 clearReference。
 *
 * <p>盲区：ERLE / 估计延迟 / 滤波器收敛这类统计量测不了，也不该测；参考帧与麦克风帧的对齐
 * 算法本身已由 ReferenceFeedTest 覆盖，协议套件不重复。
 */
class RecordingAecService extends AecService {

    /** 一次参考帧下发的记录 */
    record Reference(String sessionId, byte[] frame, long timestamp) {
    }

    private final Set<String> initialized = ConcurrentHashMap.newKeySet();
    private final Set<String> serverAecRequired = ConcurrentHashMap.newKeySet();
    private final List<String> initCalls = new CopyOnWriteArrayList<>();
    private final List<String> resetCalls = new CopyOnWriteArrayList<>();
    private final List<String> clearCalls = new CopyOnWriteArrayList<>();
    private final List<Reference> references = new CopyOnWriteArrayList<>();
    private final List<Boolean> requiredFlags = new CopyOnWriteArrayList<>();

    @Override
    public void initSession(String sessionId) {
        initCalls.add(sessionId);
        initialized.add(sessionId);
    }

    @Override
    public void setServerAecRequired(String sessionId, boolean required) {
        requiredFlags.add(required);
        if (required) {
            serverAecRequired.add(sessionId);
            initSession(sessionId);
        } else {
            serverAecRequired.remove(sessionId);
            initialized.remove(sessionId);
        }
    }

    @Override
    public void resetSession(String sessionId) {
        resetCalls.add(sessionId);
        initialized.remove(sessionId);
    }

    @Override
    public void feedReference(String sessionId, byte[] opusFrame, long timestamp) {
        references.add(new Reference(sessionId, opusFrame, timestamp));
    }

    @Override
    public byte[] process(String sessionId, byte[] micPcm, long echoTimestamp) {
        return micPcm;
    }

    @Override
    public boolean isActive(String sessionId) {
        return serverAecRequired.contains(sessionId) && initialized.contains(sessionId);
    }

    @Override
    public void clearReference(String sessionId) {
        clearCalls.add(sessionId);
    }

    // ========== 断言入口 ==========

    List<String> initCalls() {
        return List.copyOf(initCalls);
    }

    List<String> resetCalls() {
        return List.copyOf(resetCalls);
    }

    List<String> clearReferenceCalls() {
        return List.copyOf(clearCalls);
    }

    /** setServerAecRequired 的取值序列，可断言 hello 反复携带 features.aec 时的开关翻转 */
    List<Boolean> serverAecRequiredFlags() {
        return List.copyOf(requiredFlags);
    }

    /** 下发的参考帧流水，按顺序 */
    List<Reference> references() {
        return List.copyOf(references);
    }

    List<Long> referenceTimestamps() {
        return references.stream().map(Reference::timestamp).toList();
    }
}
