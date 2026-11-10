package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具执行进度决定模型的沉默要不要顺延：执行中整段顺延，执行完从结束那一刻重新起算，本轮没调过工具就不顺延。
 */
class ToolCallActivityRegistryTest {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    private final ToolCallActivityRegistry registry = new ToolCallActivityRegistry();

    @Test
    void turnWithoutToolsHasNothingToWaitFor() {
        assertThat(registry.remainingQuiet("device#1", LIMIT)).isZero();
        assertThat(registry.remainingQuiet(null, LIMIT)).isZero();
    }

    @Test
    void runningToolPostponesTheWholeLimit() {
        registry.begin("device#1");

        assertThat(registry.remainingQuiet("device#1", LIMIT)).isEqualTo(LIMIT);
    }

    @Test
    void finishedToolRestartsTheCountFromItsEnd() {
        registry.begin("device#1");
        registry.end("device#1");

        assertThat(registry.remainingQuiet("device#1", LIMIT)).isPositive().isLessThanOrEqualTo(LIMIT);
    }

    /** 同一批里串行执行多个工具，最后一个结束之前都算在执行 */
    @Test
    void overlappingToolsCountAsRunningUntilTheLastEnds() {
        registry.begin("device#1");
        registry.begin("device#1");
        registry.end("device#1");

        assertThat(registry.remainingQuiet("device#1", LIMIT)).isEqualTo(LIMIT);
    }

    /** 轮次号是毫秒时间戳，不同设备会撞号，键里必须带 owner */
    @Test
    void sameTurnIdOnAnotherDeviceIsSeparate() {
        registry.begin(ToolCallActivityRegistry.key("device-a", 1L));

        assertThat(registry.remainingQuiet(ToolCallActivityRegistry.key("device-b", 1L), LIMIT)).isZero();
    }

    @Test
    void keyIsReadFromTheToolContext() {
        Map<String, Object> toolContext = Map.of(ToolSession.CONTEXT_OWNER_ID, "device-a", ToolSession.CONTEXT_TURN_ID, 7L);

        assertThat(ToolCallActivityRegistry.keyOf(toolContext)).isEqualTo(ToolCallActivityRegistry.key("device-a", 7L));
        assertThat(ToolCallActivityRegistry.keyOf(Map.of(ToolSession.CONTEXT_TURN_ID, 7L))).isNull();
    }
}
