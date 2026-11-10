package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.stt.SttResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 说话超过单段上限时换流识别，中间段的终稿先作为进度文本下发，最后一段收句后下发整句。
 * 设备按到达顺序刷新显示，所以整句必须是最后到的那一条。
 */
class SpeechTurnTest {

    private static final Duration MERGE_WAIT = Duration.ofSeconds(5);

    /**
     * 最后一段在 merge 里等的就是中间段登记终稿；中间段一登记、还没来得及发进度文本，
     * 最后一段就可能被放行并抢先下发整句。这里把中间段卡在「已登记、未下发」的那一刻，
     * 让最后一段在这个窗口里跑，断言整句仍排在进度文本之后。
     */
    @Test
    void finalSentenceIsAnnouncedAfterTheProgressTextItRaces() throws Exception {
        SpeechTurn turn = new SpeechTurn();
        SpeechTurn.Segment first = rotatedSegment(turn);
        SpeechTurn.Segment last = turn.newSegment(sink());
        List<String> announced = new CopyOnWriteArrayList<>();
        CountDownLatch firstRegistered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch finalDone = new CountDownLatch(1);

        Thread firstSegment = Thread.ofVirtual().start(() ->
            turn.completeRotated(first, SttResult.textOnly("前半段，"), soFar -> {
                firstRegistered.countDown();
                await(releaseFirst);
                announced.add(soFar);
            }));
        assertThat(firstRegistered.await(5, TimeUnit.SECONDS)).isTrue();

        Thread lastSegment = Thread.ofVirtual().start(() -> {
            SttResult merged = turn.merge(last, SttResult.textOnly("后半段。"), MERGE_WAIT);
            turn.announceFinal(merged.text(), announced::add);
            finalDone.countDown();
        });
        // 最后一段此刻应当被挡在下发之前；给它一点时间，确认它没有抢跑
        assertThat(finalDone.await(300, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirst.countDown();
        firstSegment.join(5_000);
        lastSegment.join(5_000);

        assertThat(announced).containsExactly("前半段，", "前半段，后半段。");
    }

    /** 最后一段等不及、超时跳过了某个中间段而先下发了整句，迟到的进度文本不能再把它覆盖成半句 */
    @Test
    void lateProgressTextIsDroppedOnceTheFinalSentenceWasAnnounced() {
        SpeechTurn turn = new SpeechTurn();
        SpeechTurn.Segment slow = rotatedSegment(turn);
        List<String> announced = new CopyOnWriteArrayList<>();

        turn.announceFinal("整句", announced::add);
        turn.completeRotated(slow, SttResult.textOnly("迟到的前半段"), announced::add);

        assertThat(announced).containsExactly("整句");
    }

    @Test
    void progressTextAccumulatesAcrossRotatedSegments() {
        SpeechTurn turn = new SpeechTurn();
        SpeechTurn.Segment first = rotatedSegment(turn);
        SpeechTurn.Segment second = rotatedSegment(turn);
        List<String> announced = new CopyOnWriteArrayList<>();

        turn.completeRotated(first, SttResult.textOnly("一，"), announced::add);
        turn.completeRotated(second, SttResult.textOnly("二，"), announced::add);

        assertThat(announced).containsExactly("一，", "一，二，");
    }

    private static SpeechTurn.Segment rotatedSegment(SpeechTurn turn) {
        SpeechTurn.Segment segment = turn.newSegment(sink());
        segment.markRotated(List.of());
        return segment;
    }

    private static Sinks.Many<byte[]> sink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
