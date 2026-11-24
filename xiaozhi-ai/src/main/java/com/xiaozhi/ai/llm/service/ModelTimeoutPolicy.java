package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.tool.ToolCallActivityRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 模型流的首字与静默超时，等工具结果的那段不算沉默。
 * 超时链：工具执行上限（XiaoZhiToolCallingManager.TOOL_CALL_TIMEOUT）期间顺延；前端流空闲超时须大于工具上限加静默超时
 */
@Component
public class ModelTimeoutPolicy {

    private final Duration firstToken;
    private final Duration idle;
    private final ToolCallActivityRegistry toolCallActivity;

    @Autowired
    public ModelTimeoutPolicy(@Value("${xiaozhi.llm.first-token-timeout-seconds:30}") long firstTokenSeconds,
                              @Value("${xiaozhi.llm.idle-timeout-seconds:90}") long idleSeconds,
                              ToolCallActivityRegistry toolCallActivity) {
        this(Duration.ofSeconds(firstTokenSeconds), Duration.ofSeconds(idleSeconds), toolCallActivity);
    }

    public ModelTimeoutPolicy(Duration firstToken, Duration idle, ToolCallActivityRegistry toolCallActivity) {
        this.firstToken = firstToken;
        this.idle = idle;
        this.toolCallActivity = toolCallActivity;
    }

    /**
     * 首字或两次输出之间沉默超限时以 {@link java.util.concurrent.TimeoutException} 结束
     *
     * @param activityKey 本轮的 {@link ToolCallActivityRegistry#key}
     */
    public <T> Flux<T> guard(Flux<T> stream, String activityKey) {
        return stream.timeout(silence(activityKey, firstToken, firstToken),
                item -> silence(activityKey, idle, idle));
    }

    /** 到点先看工具：还在执行就整段顺延，刚执行完就从结束那一刻重新起算 */
    private Mono<Long> silence(String activityKey, Duration limit, Duration wait) {
        return Mono.delay(wait).flatMap(tick -> {
            Duration left = toolCallActivity.remainingQuiet(activityKey, limit);
            return left.isZero() ? Mono.just(tick) : silence(activityKey, limit, left);
        });
    }
}
