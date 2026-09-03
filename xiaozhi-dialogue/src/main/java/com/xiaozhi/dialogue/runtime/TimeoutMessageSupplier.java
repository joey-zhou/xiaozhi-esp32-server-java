package com.xiaozhi.dialogue.runtime;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 会话空闲超时、服务端主动退出时的提示语。
 * 硬约束：这个语境下用户什么都没说，话术不得以"好的""收到""明白"这类应答开头，
 * 也不得预设用户刚告别过。用户明确说再见时用 {@link GoodbyeMessageSupplier}。
 */
@Component
public class TimeoutMessageSupplier implements Supplier<String> {

    private static final Random random = new Random();

    // 超时提示语列表
    static final List<String> TIMEOUT_MESSAGES = Arrays.asList(
            "你好像在忙别的事情，我先退下啦~",
            "看来你暂时不需要我了，我先休息一下~",
            "你有一会儿没说话了，我先去充电啦~",
            "我先安静一会儿，想聊天的时候再叫我~",
            "看来你有别的事情要忙，我先离开啦~",
            "你有段时间没说话了，我先去休息了~");

    @Override
    public String get() {
        return TIMEOUT_MESSAGES.get(random.nextInt(TIMEOUT_MESSAGES.size()));
    }
}
