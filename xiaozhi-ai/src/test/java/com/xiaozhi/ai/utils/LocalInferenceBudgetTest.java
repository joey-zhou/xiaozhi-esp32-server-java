package com.xiaozhi.ai.utils;

import com.xiaozhi.ai.utils.LocalInferenceBudget.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住本地推理核预算的分配口径：只在正在用的几方之间分，有进有出时当场重分。
 */
class LocalInferenceBudgetTest {

    private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(10);

    private final AtomicLong now = new AtomicLong(1);
    private final LocalInferenceBudget budget = new LocalInferenceBudget(8, IDLE_NANOS, now::get);

    @Test
    void soleUserTakesTheLionShareButLeavesRoomForTheMainPipeline() {
        List<Integer> tts = record(Kind.TTS);

        budget.touch(Kind.TTS);

        // 只有本地合成在用：拿四分之三，不再被卡在核数/4
        assertThat(tts).containsExactly(6);
    }

    @Test
    void newcomerTriggersResplitAmongActiveKinds() {
        List<Integer> tts = record(Kind.TTS);
        List<Integer> stt = record(Kind.STT);

        budget.touch(Kind.TTS);
        budget.touch(Kind.STT);

        // 两方都在用时按 2:1 分
        assertThat(tts).containsExactly(6, 2);
        assertThat(stt).containsExactly(5);
    }

    @Test
    void idleKindGivesItsShareBack() {
        List<Integer> tts = record(Kind.TTS);
        record(Kind.STT);
        budget.touch(Kind.TTS);
        budget.touch(Kind.STT);

        // 角色把识别换成了云端：识别不再推理，合成继续在用
        now.addAndGet(IDLE_NANOS + 1);
        budget.touch(Kind.TTS);

        assertThat(tts).containsExactly(6, 2, 6);
    }

    @Test
    void steadyUseDoesNotResize() {
        List<Integer> tts = record(Kind.TTS);

        for (int i = 0; i < 100; i++) {
            now.addAndGet(TimeUnit.SECONDS.toNanos(30));
            budget.touch(Kind.TTS);
        }

        assertThat(tts).containsExactly(6);
    }

    @Test
    void unboundKindStillCountsAgainstOthers() {
        // 并发被运维配死的一方不登记回调，但它占着的核要算进别人的分母
        List<Integer> tts = record(Kind.TTS);

        budget.touch(Kind.STT);
        budget.touch(Kind.TTS);

        assertThat(tts).containsExactly(2);
    }

    @Test
    void coresForCountsTheAskerAsActive() {
        assertThat(budget.coresFor(Kind.TTS)).isEqualTo(6);

        budget.touch(Kind.STT);

        assertThat(budget.coresFor(Kind.TTS)).isEqualTo(2);
        assertThat(budget.coresFor(Kind.STT)).isEqualTo(6);
    }

    @Test
    void everyKindGetsAtLeastOneCoreOnTinyMachines() {
        LocalInferenceBudget tiny = new LocalInferenceBudget(1, IDLE_NANOS, now::get);
        tiny.touch(Kind.STT);
        tiny.touch(Kind.TTS);

        assertThat(tiny.coresFor(Kind.STT)).isEqualTo(1);
        assertThat(tiny.coresFor(Kind.TTS)).isEqualTo(1);
    }

    private List<Integer> record(Kind kind) {
        List<Integer> seen = new ArrayList<>();
        budget.bind(kind, seen::add);
        return seen;
    }
}
