package com.xiaozhi.dialogue.runtime;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Goodbye时，是否需要一些上下文信息？
 * 例如：用户名、用户ID、用户IP、用户设备信息、用户地理位置、用户行为轨迹、用户历史对话记录。
 */
@Component
public class GoodbyeMessageSupplier implements Supplier<String> {

    private static final Random random = new Random();

    // 添加告别语列表
    private static final List<String> goodbyeMessages = Arrays.asList(
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
        return goodbyeMessages.get(random.nextInt(goodbyeMessages.size()));
    }
}
