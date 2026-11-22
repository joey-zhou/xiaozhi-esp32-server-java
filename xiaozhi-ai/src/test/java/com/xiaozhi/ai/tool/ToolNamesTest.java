package com.xiaozhi.ai.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交给模型的函数名只能是字母、数字、下划线、连字符（中文与设备 MCP 一样放行），最长 64：
 * 设备上报的名字带空格、点号或太长时，部分模型接口会把整轮请求判成 400
 */
class ToolNamesTest {

    @Test
    void charactersOutsideTheAllowedSetBecomeUnderscores() {
        assertThat(ToolNames.of("iot_living room.light")).isEqualTo("iot_living_room_light");
        assertThat(ToolNames.of("iot_客厅灯")).isEqualTo("iot_客厅灯");
    }

    @Test
    void aLongNameIsCutTo64AndStaysDistinct() {
        String a = ToolNames.of("iot_" + "x".repeat(80) + "_power");
        String b = ToolNames.of("iot_" + "x".repeat(80) + "_color");

        assertThat(a).hasSize(64);
        assertThat(b).hasSize(64);
        assertThat(a).isNotEqualTo(b);
        assertThat(ToolNames.of("iot_" + "x".repeat(80) + "_power")).isEqualTo(a);
    }

    @Test
    void aShortNameIsLeftAlone() {
        assertThat(ToolNames.of("iot_speaker_mute")).isEqualTo("iot_speaker_mute");
    }
}
