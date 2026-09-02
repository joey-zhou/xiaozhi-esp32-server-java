package com.xiaozhi.communication.protocol;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 不起 Spring 容器的事件派发替身。
 *
 * <p>{@link #register} 注册的 bean 会被反射扫描，凡带 {@code @EventListener} 的方法
 * 都按参数类型同步派发。相比手工把监听器接线成回调，这样能捕获"生产代码把 @EventListener
 * 注解删了"这类回归。协议链路上必须注册的 bean：DialogueService（ChatAbortedEvent）、
 * SessionManager（ChatAudioOpenedEvent / DeviceUpdatedEvent）、VadService（TtsPlaybackCompletedEvent）。
 *
 * <p>盲区：真实 Spring 对同一事件的多个监听器不保证顺序，本替身按 bean 注册顺序同步派发，
 * 用例不要断言跨监听器的先后（例如 MessageSendOrchestrator 与 DialogueService 谁先收到
 * ChatAbortedEvent）。也不支持 condition、@Async、@TransactionalEventListener 与泛型事件解析。
 */
class TestEventBus {

    private final List<Object> beans = new CopyOnWriteArrayList<>();
    private final List<Object> published = new CopyOnWriteArrayList<>();

    void register(Object bean) {
        beans.add(bean);
    }

    void publish(Object event) {
        published.add(event);
        for (Object bean : beans) {
            for (Method method : bean.getClass().getMethods()) {
                EventListener annotation = method.getAnnotation(EventListener.class);
                if (annotation == null || !matches(annotation, method, event)) {
                    continue;
                }
                invoke(bean, method, event);
            }
        }
    }

    /** 供 MessageSender / DialogueService 这类只需要 ApplicationEventPublisher 的协作者直接引用 */
    org.springframework.context.ApplicationEventPublisher publisher() {
        return this::publish;
    }

    /** 供 MessageHandler / SessionManager 这类注入了整个 ApplicationContext 的协作者使用 */
    ApplicationContext applicationContext() {
        ApplicationContext context = mock(ApplicationContext.class);
        doAnswer(invocation -> {
            publish(invocation.getArgument(0));
            return null;
        }).when(context).publishEvent(any(Object.class));
        doAnswer(invocation -> {
            publish(invocation.getArgument(0));
            return null;
        }).when(context).publishEvent(any(ApplicationEvent.class));
        return context;
    }

    // ========== 断言入口 ==========

    List<Object> published() {
        return List.copyOf(published);
    }

    <T> List<T> eventsOf(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }

    void clear() {
        published.clear();
    }

    private static boolean matches(EventListener annotation, Method method, Object event) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1) {
            return parameterTypes[0].isInstance(event);
        }
        if (parameterTypes.length > 1) {
            return false;
        }
        for (Class<?> declared : annotation.classes()) {
            if (declared.isInstance(event)) {
                return true;
            }
        }
        return false;
    }

    private static void invoke(Object bean, Method method, Object event) {
        try {
            if (method.getParameterCount() == 0) {
                method.invoke(bean);
            } else {
                method.invoke(bean, event);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("事件监听器执行失败: " + method, cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("事件监听器不可访问: " + method, e);
        }
    }
}
