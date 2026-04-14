package com.xiaozhi.ai.stt;

/**
 * STT 识别结果。
 * 情感字段仅在支持情感识别的模型下有值，其余为 null。
 *
 * <p>各字段说明：
 * <ul>
 *   <li>text - 识别文本</li>
 *   <li>emotion - 情感标签，如 happy / neutral / angry / sad 等</li>
 *   <li>emotionScore - 情感置信度（0~1）</li>
 *   <li>emotionDegree - 情感强度标签，如 weak / moderate / strong（火山引擎）</li>
 *   <li>emotionDegreeScore - 情感强度置信度（0~1）（火山引擎）</li>
 * </ul>
 */
public record SttResult(
        String text,
        String emotion,
        Double emotionScore,
        String emotionDegree,
        Double emotionDegreeScore
) {

    /**
     * 仅含文本，无情感信息。
     */
    public static SttResult textOnly(String text) {
        return new SttResult(text, null, null, null, null);
    }

    /**
     * 含文本和情感信息（阿里云 paraformer 使用）。
     */
    public static SttResult withEmotion(String text, String emotion, Double emotionScore) {
        return new SttResult(text, emotion, emotionScore, null, null);
    }

    /**
     * 含文本和完整情感信息（火山引擎使用）。
     */
    public static SttResult withFullEmotion(String text, String emotion, Double emotionScore,
                                            String emotionDegree, Double emotionDegreeScore) {
        return new SttResult(text, emotion, emotionScore, emotionDegree, emotionDegreeScore);
    }

    public boolean hasEmotion() {
        return emotion != null && !emotion.isEmpty();
    }
}
