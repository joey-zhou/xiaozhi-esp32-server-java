package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.tool.ToolCallActivityRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 首字与两次输出之间计时，等工具结果的那段不算沉默 */
class ModelTimeoutPolicyTest {

    private static final Duration LIMIT = Duration.ofMillis(200);
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String KEY = ToolCallActivityRegistry.key("web:9", 1L);

    private final ToolCallActivityRegistry activity = new ToolCallActivityRegistry();
    private final ModelTimeoutPolicy policy = new ModelTimeoutPolicy(LIMIT, LIMIT, activity);

    @Test
    void noFirstTokenTimesOut() {
        assertThatThrownBy(() -> policy.guard(Flux.never(), KEY).blockLast(WAIT))
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void silenceAfterSomeOutputTimesOut() {
        List<String> seen = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> policy.guard(Flux.concat(Flux.just("从前"), Flux.never()), KEY)
                .doOnNext(seen::add).blockLast(WAIT))
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(seen).containsExactly("从前");
    }

    @Test
    void steadyOutputLongerThanTheLimitIsNotCut() {
        Flux<String> steady = Flux.just("一", "二", "三", "四").delayElements(LIMIT.dividedBy(2));

        assertThat(policy.guard(steady, KEY).collectList().block(WAIT)).containsExactly("一", "二", "三", "四");
    }

    @Test
    void aRunningToolIsNotSilence() {
        Flux<String> toolThenAnswer = Mono.fromRunnable(() -> activity.begin(KEY))
                .then(Mono.delay(LIMIT.multipliedBy(3)))
                .doOnNext(tick -> activity.end(KEY))
                .thenMany(Flux.just("查好了"));

        assertThat(policy.guard(toolThenAnswer, KEY).collectList().block(WAIT)).containsExactly("查好了");
    }
}
