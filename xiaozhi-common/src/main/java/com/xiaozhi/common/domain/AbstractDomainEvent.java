package com.xiaozhi.common.domain;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类，同时兼容 Spring {@link ApplicationEvent} 事件分发机制。
 * <p>
 * 所有领域事件继承此类即可同时获得：
 * <ul>
 *   <li>DomainEvent 语义（eventId + occurredOn）</li>
 *   <li>Spring ApplicationEvent 分发能力</li>
 * </ul>
 */
public abstract class AbstractDomainEvent extends ApplicationEvent implements DomainEvent {

    private final String eventId;
    private final Instant occurredOn;

    protected AbstractDomainEvent(Object source) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.occurredOn = Instant.now();
    }

    @Override
    public String eventId() {
        return eventId;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}
