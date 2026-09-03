package com.xiaozhi.dialogue.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超时提示语用在「用户很久没说话、服务端主动退出」的语境：没人说过再见，
 * 话术不得以应答词开头，也不得与告别语共用同一条。
 */
class TimeoutMessageSupplierTest {

    /** 应答式开头，预设了用户刚说过话 */
    private static final List<String> RESPONSE_OPENINGS = List.of("好的", "好哒", "好嘞", "收到", "明白", "行");

    @Test
    void noMessageAnswersSomethingTheUserNeverSaid() {
        assertThat(TimeoutMessageSupplier.TIMEOUT_MESSAGES).isNotEmpty();
        assertThat(TimeoutMessageSupplier.TIMEOUT_MESSAGES).allSatisfy(message -> {
            assertThat(message).isNotBlank();
            assertThat(RESPONSE_OPENINGS).noneMatch(message::startsWith);
        });
    }

    @Test
    void timeoutAndGoodbyeMessagesDoNotOverlap() {
        assertThat(TimeoutMessageSupplier.TIMEOUT_MESSAGES)
                .doesNotContainAnyElementsOf(GoodbyeMessageSupplier.GOODBYE_MESSAGES);
    }

    @Test
    void suppliesOnlyConfiguredMessages() {
        TimeoutMessageSupplier supplier = new TimeoutMessageSupplier();

        assertThat(supplier.get()).isIn(TimeoutMessageSupplier.TIMEOUT_MESSAGES);
    }
}
