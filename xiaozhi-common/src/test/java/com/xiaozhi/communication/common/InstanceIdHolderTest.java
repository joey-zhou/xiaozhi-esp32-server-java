package com.xiaozhi.communication.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实例标识在整个部署内唯一：同机的 server 与 dialogue 主机名相同，没配标识时要靠端口区分开，
 * 否则两个进程会订阅同一个跨实例调用频道，server 抢先回一句「不提供这项调用」就把设备工具调用搞失败；
 * 同一个进程重启后标识不变，启动时才认得出上次留下的设备归属。
 */
class InstanceIdHolderTest {

    @Test
    void theConfiguredIdIsUsedAsIs() {
        assertThat(new InstanceIdHolder("node-a", "8092").getInstanceId()).isEqualTo("node-a");
    }

    @Test
    void twoProcessesOnOneHostGetDifferentIds() {
        String server = new InstanceIdHolder("", "8091").getInstanceId();
        String dialogue = new InstanceIdHolder("", "8092").getInstanceId();

        assertThat(server).isNotEqualTo(dialogue).endsWith(":8091");
        assertThat(dialogue).endsWith(":8092");
    }

    @Test
    void aRestartedProcessKeepsItsId() {
        assertThat(new InstanceIdHolder(null, "8092").getInstanceId())
            .isEqualTo(new InstanceIdHolder(null, "8092").getInstanceId());
    }
}
