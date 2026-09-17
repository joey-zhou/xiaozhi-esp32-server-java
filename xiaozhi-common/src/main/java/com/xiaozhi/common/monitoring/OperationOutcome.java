package com.xiaozhi.common.monitoring;

/**
 * 以返回值表达成败的操作结果。
 * <p>
 * 失败不抛异常、而是随返回值一起交给调用方：调用方要区分「操作失败」与「操作成功但结果为空」，
 * 必须看 {@link #failureReason()}，不能只看业务字段是否为空。
 *
 * <p>{@link #failureReason()} 直接作为指标标签值，取值必须是有限的短码，
 * 不得放入错误详情、会话 ID 等高基数内容。
 */
public interface OperationOutcome {

    /**
     * 失败原因短码，成功时返回 null。
     */
    String failureReason();

    /**
     * 本次操作是否失败。
     */
    default boolean operationFailed() {
        return failureReason() != null;
    }
}
