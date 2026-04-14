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
}
