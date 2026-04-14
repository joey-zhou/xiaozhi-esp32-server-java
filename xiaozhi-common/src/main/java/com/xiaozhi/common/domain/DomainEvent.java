package com.xiaozhi.common.domain;

import java.time.Instant;

/**
 * 领域事件标记接口。
 * 所有领域事件均应实现此接口，提供事件 ID 和发生时间。
 * <p>
 * 现有事件同时继承 {@link org.springframework.context.ApplicationEvent}（Spring 事件分发）
 * 和实现本接口（领域语义标识），两者职责互补。
 */
public interface DomainEvent {

    /**
     * 事件唯一标识，用于幂等和审计追踪。
     */
    String eventId();

    /**
     * 事件发生的时间点。
     */
    Instant occurredOn();
}
