package com.xiaozhi.dialogue.audio;

import com.xiaozhi.utils.OpusProcessor;

import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig;
import dev.onvoid.webrtc.media.audio.AudioProcessingStats;
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
/**
 * 服务端 AEC（回声消除）服务。
 * 使用 WebRTC AEC3 在服务端消除麦克风中的扬声器回声，
 * 使不带硬件 AEC 的设备也能正常打断和对话。
 *
 * 核心设计：
 * - render/capture 以采集块为节拍：每处理一块麦克风前先喂参考，常态一块，无真参考用静音块，
 *   保证 AEC3 的延迟估计不因 underrun/overrun 被清空。
 * - 参考侧保留两帧积压吸收上行抖动，播放器预缓冲三帧让设备端队列更深，参考仍领先设备播放点约一帧；
 *   喂晚（非因果）AEC3 完全失效，实测死状为 ERLE≈0.2dB 且 delay 估成假值。
 * - 设备回显时间戳用于丢弃陈旧参考帧，以及参考落后回显点时追赶。
 * - 播放器在句间、暂停、断流期间持续下发静音帧，设备播放时间轴整轮连续，对齐不随句子重建。
 *
 * @see ReferenceFeed
 */
@Slf4j
@Service
public class AecService {
    @Value("${aec.noise.suppression.level:MODERATE}")
    private String noiseSuppressionLevel;

    // 参考侧保留的积压帧数，吸收上行抖动；须小于播放器的预缓冲帧数，参考才能领先设备播放点
    @Value("${aec.reference.backlog.frames:2}")
    private int referenceBacklogFrames;

    // 每会话 AEC 状态
    private final ConcurrentHashMap<String, AecState> states = new ConcurrentHashMap<>();

    // 声明了 hello features.aec 的会话。未声明的设备要么自己做了 AEC，要么播放期直接关麦
    private final Set<String> serverAecSessions = ConcurrentHashMap.newKeySet();

    // WebRTC native 库不可用时置位（如打包机与运行机平台不匹配导致 NoClassDefFoundError）。
    // 置位后 AEC 整体降级：不再尝试初始化，也不阻断设备连接与对话。
    private volatile boolean nativeUnavailable = false;

    // 10ms 帧参数 (16kHz mono, 16-bit)
    private static final int FRAME_BYTES_10MS = ReferenceFeed.BLOCK_BYTES;

    /**
     * 确保会话的 AEC 状态已初始化。
     * 如果已存在则复用（保留已收敛的滤波器状态），不存在才新建。
     */
    public void initSession(String sessionId) {
        if (nativeUnavailable) return;
        if (!serverAecSessions.contains(sessionId)) return;
        if (states.containsKey(sessionId)) return;
        try {
            states.putIfAbsent(sessionId, new AecState());
        } catch (Throwable t) {
            // NoClassDefFoundError 等 native 初始化失败属于 Error，不能只 catch Exception，
            // 否则会击穿到调用方（连接建立流程）导致设备无法连接。
            // 这里降级：标记 native 不可用，后续不再尝试，AEC 静默关闭但不影响连接与对话。
            nativeUnavailable = true;
            log.warn("AEC native 库初始化失败，已降级关闭回声消除（不影响设备连接与对话）。" +
                    "可能原因：webrtc-java native 库缺失。详见异常堆栈定位具体原因。SessionId: {}", sessionId, t);
        }
    }

    /**
     * 记录设备是否要求服务端做 AEC，来自 hello 的 features.aec。
     */
    public void setServerAecRequired(String sessionId, boolean required) {
        if (required) {
            serverAecSessions.add(sessionId);
            initSession(sessionId);
        } else {
            serverAecSessions.remove(sessionId);
            destroyState(sessionId);
        }
    }

    /**
     * 重置（销毁）会话的 AEC 状态
     */
    public void resetSession(String sessionId) {
        serverAecSessions.remove(sessionId);
        destroyState(sessionId);
    }

    private void destroyState(String sessionId) {
        AecState state = states.remove(sessionId);
        if (state != null) {
            // 在 apmLock 内 dispose，确保等待正在进行的 processStream/processReverseStream 完成
            synchronized (state.apmLock) {
                state.dispose();
            }
        }
    }

    /**
     * 缓存参考信号（下发给设备的 Opus 帧，含静音帧），由 process() 按采集节拍喂给 AEC3。
     */
    public void feedReference(String sessionId, byte[] opusFrame, long timestamp) {
        AecState state = states.get(sessionId);
        if (state == null) return;

        try {
            // 解码必须按发送顺序进行，解码器有状态
            byte[] pcm = state.refDecoder.opusToPcm(opusFrame);
            if (pcm == null || pcm.length == 0) return;

            synchronized (state.apmLock) {
                if (state.disposed) return;
                state.feed.add(timestamp, pcm);
            }
        } catch (Exception e) {
            log.warn("AEC feedReference 失败 - SessionId: {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 处理麦克风 PCM 数据，消除回声。
     *
     * @param echoTimestamp 设备回显的下行帧时间戳，语义为「采集本帧时喇叭正在播的那一帧」。
     *                      0 表示无参考信息（未在播放 / 协议不带时间戳）。
     */
    public byte[] process(String sessionId, byte[] micPcm, long echoTimestamp) {
        AecState state = states.get(sessionId);
        if (state == null) return micPcm;

        try {
            int totalBytes = micPcm.length;
            byte[] aecOutput = new byte[totalBytes];
            int offset = 0;
            int outOffset = 0;

            synchronized (state.apmLock) {
                if (state.disposed) return micPcm;

                if (state.feed.onEchoTimestamp(echoTimestamp)) {
                    log.info("AEC 进入时间戳对齐模式");
                }

                // 每处理一块麦克风前先喂参考，render/capture 节拍恒定，
                // AEC3 的延迟估计既不会因 underrun 被清空，也不会因批量灌入 overrun 复位
                while (offset + FRAME_BYTES_10MS <= totalBytes) {
                    for (byte[] refBlock : state.feed.blocksForCaptureBlock()) {
                        byte[] refOutput = new byte[FRAME_BYTES_10MS];
                        state.apm.processReverseStream(refBlock, state.streamConfig, state.streamConfig, refOutput);
                    }

                    byte[] micSubFrame = new byte[FRAME_BYTES_10MS];
                    System.arraycopy(micPcm, offset, micSubFrame, 0, FRAME_BYTES_10MS);
                    byte[] outputFrame = new byte[FRAME_BYTES_10MS];
                    state.apm.processStream(micSubFrame, state.streamConfig, state.streamConfig, outputFrame);
                    System.arraycopy(outputFrame, 0, aecOutput, outOffset, FRAME_BYTES_10MS);
                    offset += FRAME_BYTES_10MS;
                    outOffset += FRAME_BYTES_10MS;
                }

                logStatsPeriodically(state, sessionId);
            }

            // 处理不足 10ms 的尾部数据
            if (offset < totalBytes) {
                System.arraycopy(micPcm, offset, aecOutput, outOffset, totalBytes - offset);
            }

            return aecOutput;
        } catch (Exception e) {
            log.warn("AEC process 失败 - SessionId: {}: {}", sessionId, e.getMessage());
            return micPcm;
        }
    }

    /**
     * 有真参考被喂入时约每 5 秒记一次统计。须在 apmLock 内调用。
     * 健康：erle 上升至数 dB（AEC3 该指标封顶 6dB）、delay 稳定；病态：erle≈0.2 且 delay≈8ms 假值（非因果/超范围）。
     */
    private void logStatsPeriodically(AecState state, String sessionId) {
        state.framesSinceStatsLog++;
        if (state.framesSinceStatsLog < 84) {
            return;
        }
        if (!state.feed.takeRealFedSinceStat()) {
            return;
        }
        state.framesSinceStatsLog = 0;
        try {
            AudioProcessingStats stats = state.apm.getStatistics();
            ReferenceFeed feed = state.feed;
            // lead：最近喂入的参考帧相对设备回显点的提前量，负数即非因果
            Long lead = feed.leadMs();
            log.info("AEC统计 - SessionId: {}, erl={}dB, erle={}dB, delay={}ms, 待喂参考={}帧, lead={}, 真参考块={}, 静音块={}, 追赶块={}, 积压峰值={}帧",
                    sessionId, String.format("%.1f", stats.echoReturnLoss),
                    String.format("%.1f", stats.echoReturnLossEnhancement),
                    stats.delayMs, feed.queuedFrames(), lead == null ? "n/a" : lead + "ms",
                    feed.realBlocks(), feed.silenceBlocks(), feed.catchUpBlocks(), feed.maxBacklogFrames());
            feed.resetStats();
        } catch (Exception e) {
            log.debug("读取AEC统计失败", e);
        }
    }

    /**
     * 该会话当前是否在做服务端 AEC。
     */
    public boolean isActive(String sessionId) {
        return states.containsKey(sessionId);
    }

    /**
     * 清空待喂入的参考帧。打断时调用：已发送未播放的帧被设备丢弃，不会产生回声，
     * 留着会在下一轮开头被当参考喂入，污染对齐。
     */
    public void clearReference(String sessionId) {
        AecState state = states.get(sessionId);
        if (state == null) return;
        synchronized (state.apmLock) {
            state.feed.clear();
        }
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

        // 参考帧调度，在 apmLock 内访问
        final ReferenceFeed feed = new ReferenceFeed(referenceBacklogFrames);
        // 统计日志节流
        int framesSinceStatsLog = 0;

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

            // 自适应增益控制（AGC2，位于处理链中 AEC 之后）。
            // 默认 maxGainDb=50 会把语音样的残余回声当人声放大后送 ASR，收紧到 12dB
            config.gainControl.enabled = true;
            config.gainControl.adaptiveDigital.enabled = true;
            config.gainControl.adaptiveDigital.maxGainDb = 12.0f;
            config.gainControl.adaptiveDigital.initialGainDb = 6.0f;

            apm.applyConfig(config);

            refDecoder = new OpusProcessor();
            streamConfig = new AudioProcessingStreamConfig(16000, 1);
        }

        void dispose() {
            disposed = true;
            try {
                apm.dispose();
            } catch (Exception e) {
                log.warn("AEC dispose 失败: {}", e.getMessage());
            }
        }
    }
}
