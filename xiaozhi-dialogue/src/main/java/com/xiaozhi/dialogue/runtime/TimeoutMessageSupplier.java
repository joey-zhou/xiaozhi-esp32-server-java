package com.xiaozhi.dialogue.runtime;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 超时消息供应器
 * 用于提供会话超时时的提示语
 */
@Component
public class TimeoutMessageSupplier implements Supplier<String> {

    private static final Random random = new Random();

    // 超时提示语列表
    private static final List<String> timeoutMessages = Arrays.asList(
            "你好像在忙别的事情，我先退下啦~",
            "看来你暂时不需要我了，我先休息一下~",
            "你有一会儿没说话了，我先去充电啦~",
            "看起来你在忙，我先不打扰了~",
            "看来你有别的事情要忙，我先离开啦~",
            "你有段时间没说话了，我先去休息了~");

    @Override
    public String get() {
        return timeoutMessages.get(random.nextInt(timeoutMessages.size()));
    }
}
