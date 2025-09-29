package com.xiaozhi.dialogue.llm.api;

/**
 * 三参数消费者接口
 */
public interface TriConsumer<T, U, V> {
    void accept(T t, U u, V v);
}
