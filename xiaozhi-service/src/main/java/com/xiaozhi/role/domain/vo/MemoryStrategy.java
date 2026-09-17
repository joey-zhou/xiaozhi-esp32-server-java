package com.xiaozhi.role.domain.vo;

/**
 * 对话记忆策略值对象。
 * <p>
 * type 对应数据库 memoryType 字段（如 "memory_window"、"memory_long_term"）。
 */
public record MemoryStrategy(String type) {

    public static MemoryStrategy defaults() {
        return new MemoryStrategy(null);
    }

    /** 按 patch 合并：patch 没给记忆类型就保留当前值，与「局部更新」语义一致 */
    public MemoryStrategy merge(MemoryStrategy patch) {
        if (patch == null || patch.type() == null) {
            return this;
        }
        return patch;
    }
}
