package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.utils.EmojiUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 句子对象，表达文本。与Speech对象相对应。用于在Flux流里表达与处理对应的文本。
 * 以顺序来保障关联性。由 Player 来处理。Sentence还是需要 普通的Text、表情符号这些的处理的。
 * 不要Path 不要音频存储在这里了，分开处理。
 */
@Slf4j
@Data
public class Sentence implements Comparable<Sentence>{
    // 用于控制句子的序列号。
    private static final AtomicInteger sentenceCounter = new AtomicInteger(0);

    // 获取句子序列号
    private int seq = sentenceCounter.incrementAndGet();

    // 可能带有颜文字的原始句子文本。
    private final String text;

    // 可以用来生成TTS的纯文本，表情符号被过滤掉，表情符号不适合TTS
    private String text4Speech =null;

    // 包含所有匹配的表情符号
    private List<String> moods=null;

    // 用于记录每一个句子的形成时间戳。
    private final Instant createdAt = Instant.now();

    public Sentence(String text) {
        this.text = text;
    }

    /**
     * 获取情感词列表的不可修改副本
     */
    public List<String> getMoods() {
        if(moods==null){
            moods = new ArrayList<>();
            this.text4Speech = EmojiUtils.processSentence(text,moods);
        }
        return Collections.unmodifiableList(moods);
    }

    public String getText4Speech() {
        if(text4Speech ==null){
            moods = new ArrayList<>();
            this.text4Speech = EmojiUtils.processSentence(text,moods);
        }
        return text4Speech;
    }

    public boolean isOnlyEmoji() {
        // 表情符号通常不超过4个字符
        return moods != null && !moods.isEmpty() &&
                (text.trim().length() <= 4);
    }

    @Override
    public int compareTo(Sentence other) {
        // 按句子的序列号排序
        return Integer.compare(this.getSeq(), other.getSeq());
    }
}
