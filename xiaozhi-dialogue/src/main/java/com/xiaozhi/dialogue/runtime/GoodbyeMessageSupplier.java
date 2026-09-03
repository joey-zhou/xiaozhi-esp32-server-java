package com.xiaozhi.dialogue.runtime;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 用户明确告别（说了再见、拜拜等）时的应答式告别语。
 * 硬约束：每条都以应答词开头，只在用户刚开口告别时成立；
 * 服务端超时主动退出用 {@link TimeoutMessageSupplier}。
 */
@Component
public class GoodbyeMessageSupplier implements Supplier<String> {

    private static final Random random = new Random();

    // 告别语列表
    static final List<String> GOODBYE_MESSAGES = Arrays.asList(
            "好的，拜拜~有需要随时叫我哦！",
            "好哒，那我先走啦，拜拜~",
            "收到！我先退下啦，有需要再叫我~",
            "明白！那我先不打扰你啦，拜拜~",
            "好的呢，有事随时呼唤我，拜拜~",
            "好哒，我先去休息一下，需要我时再叫我哦~",
            "收到！那我就先告退啦，拜拜~",
            "好的，我先离开啦，有问题随时找我~",
            "明白！我先下线休息了，需要时再唤醒我~",
            "好哒好哒，那我先走啦，回见~");

    @Override
    public String get() {
        return GOODBYE_MESSAGES.get(random.nextInt(GOODBYE_MESSAGES.size()));
    }
}
