package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.stt.SttResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次说话的识别过程。
 * <p>
 * STT 一条流从开口起最多等 90 秒，用户说得更久时每到单段上限就换一条流继续识别，
 * 一次说话由多段组成。中间各段的终稿只记下来，最后一段收句时按顺序拼成整句再回答。
 * 首字打断标志跨段共用：换流不重复暂停播放，终稿裁决只在最后一段做。
 */
@Slf4j
public final class SpeechTurn {

    private final List<Segment> segments = new CopyOnWriteArrayList<>();
    private final AtomicBoolean bargeIn = new AtomicBoolean(false);

    public Segment newSegment(Sinks.Many<byte[]> sink) {
        Segment segment = new Segment(sink);
        segments.add(segment);
        return segment;
    }

    /** 正在识别的那一段，还没起段时为 null */
    public Segment currentSegment() {
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    public AtomicBoolean bargeIn() {
        return bargeIn;
    }

    /** 已换流各段中终稿已到的文本按顺序拼接，遇到还没出终稿的段就停 */
    public String textSoFar() {
        StringBuilder text = new StringBuilder();
        for (Segment segment : segments) {
            if (!segment.rotated) {
                break;
            }
            SttResult result = segment.result.getNow(null);
            if (result == null) {
                break;
            }
            appendText(text, result);
        }
        return text.toString();
    }

    /**
     * 最后一段收句：等前面各段终稿到齐后拼成整句。
     * 只有一段时原样返回，失败码等信息不动。某段迟迟不出终稿就跳过它，不能让整句卡住。
     */
    public SttResult merge(Segment last, SttResult lastResult, Duration wait) {
        if (segments.size() == 1) {
            return lastResult;
        }
        StringBuilder text = new StringBuilder();
        SttResult emotional = null;
        for (Segment segment : segments) {
            SttResult result;
            if (segment == last) {
                result = lastResult;
            } else {
                try {
                    result = segment.result.get(wait.toMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    log.warn("前面一段识别在 {}ms 内没有终稿，拼接时跳过", wait.toMillis());
                    continue;
                }
            }
            if (result == null) {
                continue;
            }
            appendText(text, result);
            if (result.hasEmotion()) {
                emotional = result;
            }
        }
        String merged = text.toString();
        String failureReason = merged.isBlank() && lastResult != null ? lastResult.failureReason() : null;
        return emotional != null
                ? new SttResult(merged, emotional.emotion(), emotional.emotionScore(),
                        emotional.emotionDegree(), emotional.emotionDegreeScore(), failureReason)
                : new SttResult(merged, null, null, null, null, failureReason);
    }

    /** 已换流各段的整段 PCM 加上当前段的，按说话顺序，用于整次说话落盘 */
    public List<byte[]> collectPcm(List<byte[]> currentSegmentPcm) {
        List<byte[]> all = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment.rotated) {
                all.addAll(segment.pcm);
            }
        }
        all.addAll(currentSegmentPcm);
        return all;
    }

    private static void appendText(StringBuilder text, SttResult result) {
        if (StringUtils.hasText(result.text())) {
            text.append(result.text().trim());
        }
    }

    /** 一条 STT 流覆盖的那一段 */
    public static final class Segment {
        private final Sinks.Many<byte[]> sink;
        private final CompletableFuture<SttResult> result = new CompletableFuture<>();
        private volatile boolean rotated;
        private volatile List<byte[]> pcm = List.of();

        private Segment(Sinks.Many<byte[]> sink) {
            this.sink = sink;
        }

        public Sinks.Many<byte[]> sink() {
            return sink;
        }

        /** 本段已因到时长上限换流，之后它的终稿只拼接不回答 */
        public boolean isRotated() {
            return rotated;
        }

        /** 换流时 VAD 交出的本段整段 PCM，供失败重放 */
        public List<byte[]> pcm() {
            return pcm;
        }

        /** 要在终结本段音频流之前调用，段线程从 stream 返回时才看得到 */
        public void markRotated(List<byte[]> segmentPcm) {
            this.pcm = segmentPcm != null ? List.copyOf(segmentPcm) : List.of();
            this.rotated = true;
        }

        public void complete(SttResult sttResult) {
            result.complete(sttResult != null ? sttResult : SttResult.textOnly(""));
        }
    }
}
