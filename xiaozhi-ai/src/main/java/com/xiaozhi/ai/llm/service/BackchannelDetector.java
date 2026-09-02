package com.xiaozhi.ai.llm.service;

import java.util.Set;

/**
 * 附和词检测器：设备说话时用户插进来的这些话不算打断，播放应当继续。
 * 整句去掉标点、符号、空白并转小写后精确匹配，不做包含匹配（"好的"是附和，"好了"是打断）。
 * 单字若不是打断指令，按识别噪音处理。
 */
class BackchannelDetector {

    /** 续播词：语气、肯定、反应、催促、表示在听。"明白了""知道了""好了""行了"不在此列 */
    private static final Set<String> CONTINUE_WORDS = Set.of(
            "嗯", "嗯嗯", "啊", "哦", "哦哦", "噢", "喔", "呃", "唔", "哎", "诶", "欸", "嗯哼", "嗯呐", "啊这",
            "哎呀", "哎哟", "这样", "这样啊", "原来如此",
            "对", "对对", "对的", "对啊", "对呀", "对哦", "哦对", "嗯对",
            "是", "是的", "是啊", "是呀", "是吧", "是吗", "没错", "确实", "真的",
            "好", "好的", "好好", "好嘞", "好呀", "好啊", "好吧", "好滴", "嗯好", "嗯好的", "嗯嗯好",
            "行", "行行", "行啊", "行吧", "可以", "可以啊", "ok", "okay",
            "哈", "哈哈", "呵呵", "嘿嘿", "嘻嘻", "哇", "哇塞", "天哪", "我的天",
            "厉害", "牛", "太好了", "有意思", "有道理", "真的吗", "真的假的", "不会吧",
            "然后呢", "后来呢", "继续", "接着说", "接着讲", "然后", "再然后",
            "明白", "了解", "知道",
            "yeah", "yes", "yep", "yessir", "right", "sure", "alright", "gotit", "isee", "goon",
            "uhhuh", "mm", "hmm", "wow", "cool", "nice"
    );

    /** 单字里算打断的，其余单字按识别噪音处理 */
    private static final Set<String> SINGLE_CHAR_INTERRUPTS = Set.of(
            "停", "等", "拜", "不", "别", "走", "换", "啥", "错", "喂"
    );

    public boolean isBackchannel(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return true;
        }
        if (CONTINUE_WORDS.contains(normalized)) {
            return true;
        }
        // 续播词重复（嗯嗯嗯嗯、好的好的、okok）
        String unit = repeatingUnit(normalized);
        if (unit != null && CONTINUE_WORDS.contains(unit)) {
            return true;
        }
        return normalized.codePointCount(0, normalized.length()) == 1
                && !SINGLE_CHAR_INTERRUPTS.contains(normalized);
    }

    /** 文本由某个片段重复至少两次构成时返回最短片段，否则返回 null */
    private static String repeatingUnit(String text) {
        int[] cps = text.codePoints().toArray();
        for (int period = 1; period <= cps.length / 2; period++) {
            if (cps.length % period != 0) {
                continue;
            }
            boolean repeats = true;
            for (int i = period; i < cps.length && repeats; i++) {
                repeats = cps[i] == cps[i - period];
            }
            if (repeats) {
                return new String(cps, 0, period);
            }
        }
        return null;
    }

    static String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\p{P}\\p{S}\\s]", "").toLowerCase();
    }
}
