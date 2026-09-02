package com.xiaozhi.ai.llm.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备说话时插进来的话：附和续播，指令打断。整句精确匹配，"好的"和"好了"意思相反。
 */
class BackchannelDetectorTest {

    private final BackchannelDetector detector = new BackchannelDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "嗯", "嗯。", "嗯嗯嗯嗯", "啊", "哦哦", "对", "对的。", "对对对对", "是的", "没错",
            "好的", "好嘞", "行", "可以啊", "OK", "okay", "哈哈哈哈", "哇塞", "厉害",
            "然后呢？", "继续", "接着说", "明白", "了解", "原来如此", "yeah", "Uh huh", "",
            "好的好的", "对的对的", "OK OK", "okokok", "是吗", "好吧", "哎呀", "确实", "got it", "alright",
            // 单字且不是指令：按识别噪音处理，同音错字也落在这里
            "队", "恩", "号"
    })
    void backchannelKeepsPlaying(String text) {
        assertThat(detector.isBackchannel(text)).as(text).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "停", "停下", "等等", "等一下", "别说了", "闭嘴", "安静",
            "不是", "不对", "不要", "不用", "错了", "换一个", "重来", "再说一遍",
            "什么", "啥", "拜", "拜拜", "再见", "走了",
            "好了", "行了", "知道了", "明白了", "懂了", "可以了", "够了", "错", "喂",
            "小智", "今天天气怎么样", "对不对", "好不好", "no", "好的吗"
    })
    void realSpeechInterrupts(String text) {
        assertThat(detector.isBackchannel(text)).as(text).isFalse();
    }
}
