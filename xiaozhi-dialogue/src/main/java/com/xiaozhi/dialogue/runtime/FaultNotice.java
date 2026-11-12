package com.xiaozhi.dialogue.runtime;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 出故障时对用户说的话。
 * <p>
 * 设备面向普通用户，话术只讲「我怎么了、你可以怎么做」，不提识别、模型、网络、配置这类技术原因；
 * 每类几句轮换，免得每次一模一样。改措辞只改这里。
 */
public enum FaultNotice {

    /** 这一句没识别出来，多半是偶然的，请用户重说 */
    MISHEARD(
            "不好意思，我刚才没听清楚，能再说一遍吗？",
            "哎呀，刚才那句我没听明白，麻烦你再说一次。",
            "抱歉，我没听清你说的话，再跟我说一遍好吗？"),

    /** 一直听不清：连续识别失败，或者识别暂时用不了。再让用户重说只会越说越烦，请他过会儿再来 */
    CANNOT_HEAR(
            "抱歉，我这会儿有点听不清，你过一会儿再来找我好吗？",
            "不好意思，我暂时听不清你说话，等一下再跟我聊吧。"),

    /** 听清了但这次没回答出来，多半是偶然的，请用户重说 */
    DISTRACTED(
            "抱歉，我刚刚走神了，你能再说一遍吗？",
            "哎呀，我这会儿没反应过来，再说一次好吗？",
            "不好意思，我刚才没想好怎么回答，麻烦你再讲一遍。"),

    /** 暂时完全没法陪用户聊天 */
    UNAVAILABLE(
            "抱歉，我现在需要休息一会儿，你过一会儿再来找我吧。",
            "不好意思，我暂时没办法陪你聊天，过一会儿再试试好吗？");

    private final List<String> phrases;

    FaultNotice(String... phrases) {
        this.phrases = List.of(phrases);
    }

    public String pick() {
        return phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));
    }

    public List<String> phrases() {
        return phrases;
    }
}
