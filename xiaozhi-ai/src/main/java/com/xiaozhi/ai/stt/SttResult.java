package com.xiaozhi.ai.stt;

import com.xiaozhi.common.monitoring.OperationOutcome;

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
 *   <li>failureReason - 识别失败原因短码，成功为 null</li>
 * </ul>
 *
 * <p>失败与「用户没说话」是两回事：失败结果的 text 仍可能为空，也可能是失败前已识别到的部分文本，
 * 调用方要区分二者必须看 failureReason，不能只看 text 是否为空。
 */
public record SttResult(
        String text,
        String emotion,
        Double emotionScore,
        String emotionDegree,
        Double emotionDegreeScore,
        String failureReason
) implements OperationOutcome {

    /** 失败原因：上游识别服务返回错误、连接失败或会话建立失败 */
    public static final String FAILURE_UPSTREAM_ERROR = "upstream_error";

    /** 失败原因：等待识别结果超时且不保留部分文本 */
    public static final String FAILURE_TIMEOUT = "timeout";

    /** 失败原因：本地处理异常，如本地识别引擎报错、请求构造失败 */
    public static final String FAILURE_LOCAL_ERROR = "local_error";

    /**
     * 仅含文本，无情感信息。
     */
    public static SttResult textOnly(String text) {
        return new SttResult(text, null, null, null, null, null);
    }

    /**
     * 含文本和情感信息（阿里云 paraformer 使用）。
     */
    public static SttResult withEmotion(String text, String emotion, Double emotionScore) {
        return new SttResult(text, emotion, emotionScore, null, null, null);
    }

    /**
     * 含文本和完整情感信息（火山引擎使用）。
     */
    public static SttResult withFullEmotion(String text, String emotion, Double emotionScore,
                                            String emotionDegree, Double emotionDegreeScore) {
        return new SttResult(text, emotion, emotionScore, emotionDegree, emotionDegreeScore, null);
    }

    /**
     * 识别失败且无可用文本。
     */
    public static SttResult failure(String reason) {
        return new SttResult("", null, null, null, null, reason);
    }

    /**
     * 标记为失败，保留已识别到的文本与情感。reason 为 null 时原样返回。
     */
    public SttResult withFailure(String reason) {
        return reason == null
                ? this
                : new SttResult(text, emotion, emotionScore, emotionDegree, emotionDegreeScore, reason);
    }

    /**
     * 等待超时收尾用：既没识别到文本、也还没有失败原因时才记失败。
     * 已有部分文本按部分结果返回，已有更具体的失败原因时保留原因。
     */
    public SttResult withFailureIfEmpty(String reason) {
        if (failureReason != null || (text != null && !text.isBlank())) {
            return this;
        }
        return withFailure(reason);
    }

    public boolean hasEmotion() {
        return emotion != null && !emotion.isEmpty();
    }
}
