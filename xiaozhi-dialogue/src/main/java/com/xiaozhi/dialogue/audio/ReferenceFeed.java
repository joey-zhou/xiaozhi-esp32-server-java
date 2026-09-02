package com.xiaozhi.dialogue.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AEC 参考帧调度。参考按 10ms 块出让，常态与采集块严格 1:1，无真参考时给静音块。
 * 保留 backlogFrames 帧积压作为抖动缓冲：上行帧到达时刻有抖动，积压为零时每帧参考落在哪个采集块
 * 都随抖动漂移，参考流被插入静音块，AEC3 每次都要重新对齐。开播突发到达时每个采集块多喂几块
 * 真参考压到目标积压；稳态下积压超出目标三帧才追；参考落后于设备回显点一帧及以上时同样追赶。
 * 设备回显点之前 600ms 以上的参考直接丢弃。
 * 设备回显的是刚送入播放缓冲的那一帧，正常喂入时领先量为 0 到两帧，不能据此再往前追。
 */
final class ReferenceFeed {
    /** 16kHz 单声道 16bit，10ms */
    static final int BLOCK_BYTES = 320;
    static final int BLOCKS_PER_FRAME = 6;
    /** 参考帧缓冲上限（60ms/帧，约 5 秒） */
    static final int MAX_FRAMES = 84;
    /** 落后回显点超过此值的参考帧直接丢弃，AEC3 延迟估计范围只有约 600ms */
    static final long STALE_MS = 600;
    /** 默认保留的积压帧数 */
    static final int DEFAULT_BACKLOG_FRAMES = 2;
    /** 追赶时每个采集块额外多喂的真参考块数。AEC3 连续 render 块超过 26 个会判 API 调用偏斜并复位 */
    static final int CATCH_UP_EXTRA_BLOCKS = 3;
    /** 参考相对回显点落后到此值（含）视为失步，开始追赶 */
    static final long STALL_LEAD_MS = -60;
    private static final byte[] SILENCE = new byte[BLOCK_BYTES];

    /** 已发送待喂入的参考帧：下发时间戳 + 解码后的 PCM */
    record Frame(long timestamp, byte[] pcm) {}

    private final ArrayDeque<Frame> queue = new ArrayDeque<>();
    /** 稳态下积压（含当前帧剩余块）达到此块数开始追赶，降到 catchUpStopBlocks 以内停止 */
    private final int catchUpStartBlocks;
    private final int catchUpStopBlocks;
    /** 空闲后刚有参考到达：压到目标积压为止 */
    private boolean startup;
    private byte[] currentPcm;
    private int currentOffset;
    private boolean catchingUp;
    private boolean echoSeen;
    private long lastEcho;
    private long lastFed;
    private boolean realFedSinceStat;

    private int realBlocks;
    private int silenceBlocks;
    private int catchUpBlocks;
    private int maxBacklogFrames;

    ReferenceFeed() {
        this(DEFAULT_BACKLOG_FRAMES);
    }

    /**
     * @param backlogFrames 稳态保留的积压帧数，0 表示参考到帧即喂完
     */
    ReferenceFeed(int backlogFrames) {
        this.catchUpStartBlocks = (backlogFrames + 3) * BLOCKS_PER_FRAME;
        this.catchUpStopBlocks = Math.max(backlogFrames, 1) * BLOCKS_PER_FRAME;
    }

    void add(long timestamp, byte[] pcm) {
        if (currentPcm == null && queue.isEmpty()) {
            startup = true;
        }
        queue.addLast(new Frame(timestamp, pcm));
        maxBacklogFrames = Math.max(maxBacklogFrames, queue.size());
        while (queue.size() > MAX_FRAMES) {
            queue.pollFirst();
        }
    }

    /**
     * 记录设备回显的下发帧时间戳，0 表示无参考信息
     *
     * @return 是否首次收到回显时间戳
     */
    boolean onEchoTimestamp(long echoTimestamp) {
        if (echoTimestamp <= 0) {
            return false;
        }
        lastEcho = echoTimestamp;
        if (echoSeen) {
            return false;
        }
        echoSeen = true;
        return true;
    }

    /**
     * 一个采集块对应要喂入的参考块：常态一块，追赶时多喂几块真参考
     */
    List<byte[]> blocksForCaptureBlock() {
        List<byte[]> blocks = new ArrayList<>(1);
        blocks.add(nextBlock());
        if (needCatchUp()) {
            for (int i = 0; i < CATCH_UP_EXTRA_BLOCKS && hasRealBlock(); i++) {
                blocks.add(nextBlock());
                catchUpBlocks++;
            }
        }
        return blocks;
    }

    void clear() {
        queue.clear();
        currentPcm = null;
        catchingUp = false;
        startup = false;
        lastFed = 0;
    }

    int queuedFrames() {
        return queue.size();
    }

    /** 最近喂入的参考帧相对设备回显点的领先量（毫秒），无回显信息时为 null */
    Long leadMs() {
        return echoSeen && lastFed != 0 ? tsDiff(lastFed, lastEcho) : null;
    }

    boolean takeRealFedSinceStat() {
        boolean fed = realFedSinceStat;
        realFedSinceStat = false;
        return fed;
    }

    int realBlocks() {
        return realBlocks;
    }

    int silenceBlocks() {
        return silenceBlocks;
    }

    int catchUpBlocks() {
        return catchUpBlocks;
    }

    int maxBacklogFrames() {
        return maxBacklogFrames;
    }

    void resetStats() {
        realBlocks = 0;
        silenceBlocks = 0;
        catchUpBlocks = 0;
        maxBacklogFrames = 0;
    }

    /** 32 位毫秒时间戳的带符号差值，跨回绕安全 */
    static long tsDiff(long a, long b) {
        long d = (a - b) & 0xFFFFFFFFL;
        return d >= 0x80000000L ? d - 0x100000000L : d;
    }

    private boolean needCatchUp() {
        dropStale();
        int backlog = remainingBlocks() + queue.size() * BLOCKS_PER_FRAME;
        if (startup) {
            if (backlog > catchUpStopBlocks) {
                return true;
            }
            startup = false;
        }
        if (backlog >= catchUpStartBlocks) {
            catchingUp = true;
        } else if (backlog <= catchUpStopBlocks) {
            catchingUp = false;
        }
        if (catchingUp) {
            return true;
        }
        return backlog > 0 && echoSeen && lastFed != 0 && tsDiff(lastFed, lastEcho) <= STALL_LEAD_MS;
    }

    private boolean hasRealBlock() {
        if (remainingBlocks() > 0) {
            return true;
        }
        dropStale();
        return !queue.isEmpty();
    }

    private int remainingBlocks() {
        return currentPcm == null ? 0 : (currentPcm.length - currentOffset) / BLOCK_BYTES;
    }

    private void dropStale() {
        if (!echoSeen) {
            return;
        }
        Frame head;
        while ((head = queue.peekFirst()) != null && tsDiff(lastEcho, head.timestamp()) > STALE_MS) {
            queue.pollFirst();
        }
    }

    private byte[] nextBlock() {
        if (remainingBlocks() > 0) {
            byte[] block = Arrays.copyOfRange(currentPcm, currentOffset, currentOffset + BLOCK_BYTES);
            currentOffset += BLOCK_BYTES;
            realBlocks++;
            return block;
        }
        currentPcm = null;
        dropStale();
        Frame head = queue.pollFirst();
        if (head != null) {
            currentPcm = head.pcm();
            currentOffset = 0;
            lastFed = head.timestamp();
            realFedSinceStat = true;
            return nextBlock();
        }
        silenceBlocks++;
        return SILENCE.clone();
    }
}
