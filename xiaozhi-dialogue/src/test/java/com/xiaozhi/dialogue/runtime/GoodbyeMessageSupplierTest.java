package com.xiaozhi.dialogue.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告别语是对「用户刚说了再见」的应答，每条都以应答词开头；
 * 服务端超时主动退出不能用这套，改用 TimeoutMessageSupplier。
 */
class GoodbyeMessageSupplierTest {

    private static final List<String> RESPONSE_OPENINGS = List.of("好的", "好哒", "好嘞", "收到", "明白");

    @Test
    void everyMessageAnswersAnExplicitGoodbye() {
        assertThat(GoodbyeMessageSupplier.GOODBYE_MESSAGES).isNotEmpty();
        assertThat(GoodbyeMessageSupplier.GOODBYE_MESSAGES).allSatisfy(message ->
                assertThat(RESPONSE_OPENINGS).anyMatch(message::startsWith));
    }

    @Test
    void suppliesOnlyConfiguredMessages() {
        GoodbyeMessageSupplier supplier = new GoodbyeMessageSupplier();

        assertThat(supplier.get()).isIn(GoodbyeMessageSupplier.GOODBYE_MESSAGES);
    }
}
