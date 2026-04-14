package com.xiaozhi.dialogue.audio;

import com.xiaozhi.event.TtsPlaybackCompletedEvent;
import com.xiaozhi.utils.OpusProcessor;

import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig;
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 AEC（回声消除）服务。
 * 使用 WebRTC AEC3 在服务端消除麦克风中的扬声器回声，
 * 使不带硬件 AEC 的设备也能正常打断和对话。
 *
 * 核心设计：
 * - feedReference() 解码参考 Opus 帧后，立即逐子帧调用 processReverseStream，
 *   以 TTS 发送的实时节奏驱动 AEC3 参考通道，不做缓队列积压。
 * - process() 以麦克风到达的实时节奏逐子帧调用 processStream。
 * - 两者都直接驱动 AEC3，保持各自的实时时间线；AEC3 内置延迟估计器
 *   自动找到参考信号与回声之间的延迟，无需手动对齐。
 * - setStreamDelayMs 仅作为初始提示加速收敛。
 */
@Service
public class AecService {
    private static final Logger logger = LoggerFactory.getLogger(AecService.class);

    @Value("${aec.enabled:true}")
    private boolean enabled;

    @Value("${aec.stream.delay.ms:120}")
    private int streamDelayMs;

    @Value("${aec.noise.suppression.level:MODERATE}")
    private String noiseSuppressionLevel;

    // 每会话 AEC 状态
    private final ConcurrentHashMap<String, AecState> states = new ConcurrentHashMap<>();

    // 10ms 帧参数 (16kHz mono, 16-bit)
    private static final int FRAME_BYTES_10MS = 320;      // bytes

    /**
     * 确保会话的 AEC 状态已初始化。
     * 如果已存在则复用（保留已收敛的滤波器状态），不存在才新建。
     */
    public void initSession(String sessionId) {
        if (!enabled) return;
        if (states.containsKey(sessionId)) return;
        try {
            AecState existing = states.putIfAbsent(sessionId, new AecState());
            if (existing == null) {
            }
        } catch (Exception e) {
            logger.error("AEC会话初始化失败: {}", sessionId, e);
        }
    }

    /**
     * 重置（销毁）会话的 AEC 状态
     */
    public void resetSession(String sessionId) {
        AecState state = states.remove(sessionId);
        if (state != null) {
            // 在 apmLock 内 dispose，确保等待正在进行的 processStream/processReverseStream 完成
            synchronized (state.apmLock) {
                state.dispose();
            }
        }
    }

    /**
     * TTS 播放结束时重建 AEC 实例。
     * AEC3 在 TTS 停止后仍保留旧的回声滤波器，会把用户说话当回声消除（过度消除）。
     * 重建 APM 实例可以清除旧滤波器，避免误消除用户声音。
     */
    @EventListener
    public void onTtsPlaybackEnd(TtsPlaybackCompletedEvent event) {
        if (!enabled) return;
        String sessionId = event.getSessionId();
        AecState old = states.get(sessionId);
        if (old == null) return;
        try {
            AecState fresh = new AecState();
            // 原子替换：用新实例替换旧实例
            if (states.replace(sessionId, old, fresh)) {
                // 在 apmLock 内 dispose，确保等待正在进行的 processStream/processReverseStream 完成
                synchronized (old.apmLock) {
                    old.dispose();
                }
            } else {
                // 并发竞争，新实例被抢先替换，释放刚创建的
                synchronized (fresh.apmLock) {
                    fresh.dispose();
                }
            }
        } catch (Exception e) {
            logger.warn("AEC重建失败: {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 喂入参考信号（TTS 发给设备的 Opus 帧）。
     * 解码后立即逐子帧调用 processReverseStream，以 TTS 发送的实时节奏驱动 AEC3。
     * 不缓队列——队列积压会导致参考帧与麦克风帧时间线错位，使 AEC3 无法正确对齐。
     */
    public void feedReference(String sessionId, byte[] opusFrame) {
        if (!enabled) return;
        AecState state = states.get(sessionId);
        if (state == null) return;

        try {
            // 用独立解码器解码参考 Opus 帧
            byte[] pcm = state.refDecoder.opusToPcm(opusFrame);
            if (pcm == null || pcm.length == 0) return;

            // 立即逐子帧调用 processReverseStream，以 TTS 实时节奏驱动参考通道
            synchronized (state.apmLock) {
                if (state.disposed) return;
                int offset = 0;
                while (offset + FRAME_BYTES_10MS <= pcm.length) {
                    byte[] subFrame = new byte[FRAME_BYTES_10MS];
                    System.arraycopy(pcm, offset, subFrame, 0, FRAME_BYTES_10MS);
                    byte[] refOutput = new byte[FRAME_BYTES_10MS];
                    state.apm.processReverseStream(subFrame, state.streamConfig, state.streamConfig, refOutput);
                    offset += FRAME_BYTES_10MS;
                }
            }

        } catch (Exception e) {
            logger.warn("AEC feedReference 失败 - SessionId: {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 处理麦克风 PCM 数据，消除回声。
     * 以麦克风到达的实时节奏逐子帧调用 processStream。
     * AEC3 内部延迟估计器自动将参考通道与麦克风通道对齐。
     */
    public byte[] process(String sessionId, byte[] micPcm) {
        if (!enabled) return micPcm;
        AecState state = states.get(sessionId);
        if (state == null) return micPcm;

        try {
            int totalBytes = micPcm.length;
            byte[] aecOutput = new byte[totalBytes];
            int offset = 0;
            int outOffset = 0;

            synchronized (state.apmLock) {
                if (state.disposed) return micPcm;
                while (offset + FRAME_BYTES_10MS <= totalBytes) {
                    byte[] micSubFrame = new byte[FRAME_BYTES_10MS];
                    System.arraycopy(micPcm, offset, micSubFrame, 0, FRAME_BYTES_10MS);
                    byte[] outputFrame = new byte[FRAME_BYTES_10MS];
                    state.apm.processStream(micSubFrame, state.streamConfig, state.streamConfig, outputFrame);
                    System.arraycopy(outputFrame, 0, aecOutput, outOffset, FRAME_BYTES_10MS);
                    offset += FRAME_BYTES_10MS;
                    outOffset += FRAME_BYTES_10MS;
                }
            }

            // 处理不足 10ms 的尾部数据
            if (offset < totalBytes) {
                System.arraycopy(micPcm, offset, aecOutput, outOffset, totalBytes - offset);
            }

            return aecOutput;
        } catch (Exception e) {
            logger.warn("AEC process 失败 - SessionId: {}: {}", sessionId, e.getMessage());
            return micPcm;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 每会话的 AEC 状态。
     */
    private class AecState {
        final AudioProcessing apm;
        final OpusProcessor refDecoder;
        final AudioProcessingStreamConfig streamConfig;
        final Object apmLock = new Object();  // feedReference 和 process 共用同一把锁，保证 APM 调用线程安全
        volatile boolean disposed = false;     // dispose 标志，在 apmLock 内设置和检查

        AecState() {
            apm = new AudioProcessing();

            AudioProcessingConfig config = new AudioProcessingConfig();
            config.echoCanceller.enabled = true;
            config.echoCanceller.enforceHighPassFiltering = false;

            // 降噪：可配置级别
            AudioProcessingConfig.NoiseSuppression.Level nsLevel;
            try {
                nsLevel = AudioProcessingConfig.NoiseSuppression.Level.valueOf(noiseSuppressionLevel.toUpperCase());
            } catch (Exception e) {
                nsLevel = AudioProcessingConfig.NoiseSuppression.Level.LOW;
            }
            config.noiseSuppression.enabled = true;
            config.noiseSuppression.level = nsLevel;

            config.highPassFilter.enabled = true;

            // 自适应增益控制（AGC）：替代 AudioEnhancer 的固定增益+压缩，
            // 与 AEC/降噪在同一处理链内协同，不会放大残留回声
            config.gainControl.enabled = true;
            config.gainControl.adaptiveDigital.enabled = true;

            apm.applyConfig(config);

            // 设置初始延迟提示，帮助 AEC3 加速收敛（AEC3 内置延迟估计器会自动调整）
            apm.setStreamDelayMs(streamDelayMs);

            refDecoder = new OpusProcessor();
            streamConfig = new AudioProcessingStreamConfig(16000, 1);
        }

        void dispose() {
            disposed = true;
            try {
                apm.dispose();
            } catch (Exception e) {
                logger.warn("AEC dispose 失败: {}", e.getMessage());
            }
        }
    }
}
