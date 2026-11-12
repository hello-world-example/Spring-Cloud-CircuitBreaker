package com.kai.learn.cbdemo.l2.metrics;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 【是什么】监听所有熔断器的事件：状态切换、调用成功、调用失败、被拒绝……并保存最近的 100 条。
 *
 * 【为什么】熔断器打开是一个<b>需要人关注</b>的信号：说明某个下游出了问题。生产环境中通常要
 * 在状态切换时打日志、发告警（钉钉 / Slack / PagerDuty），而不是等用户反馈「页面没数据」。
 *
 * 【原理】两层事件：
 * <ul>
 *   <li><b>Registry 事件</b>（{@link RegistryEventConsumer}）：熔断器实例被创建、替换、删除。
 *       因为熔断器是在第一次调用时才懒加载创建的，所以要先监听 Registry，在实例被创建时再给它挂上监听器</li>
 *   <li><b>熔断器事件</b>（{@code cb.getEventPublisher()}）：SUCCESS / ERROR / IGNORED_ERROR / NOT_PERMITTED /
 *       STATE_TRANSITION / SLOW_CALL_RATE_EXCEEDED / FAILURE_RATE_EXCEEDED ……</li>
 * </ul>
 * resilience4j-spring-boot3 会自动把容器中的 {@code RegistryEventConsumer<CircuitBreaker>} Bean 注册到 Registry 上。
 *
 * 【坑】事件回调在<b>业务调用线程</b>中同步执行，里面不能做慢操作（比如同步调用告警接口），
 * 否则会拖慢每一次业务调用。告警应该异步发送，并且要去重、限频。
 */
@Component
@Slf4j
public class CircuitBreakerEventRecorder implements RegistryEventConsumer<CircuitBreaker> {

    private static final int MAX_EVENTS = 100;

    public record EventView(ZonedDateTime time, String breaker, String type, String detail) {
    }

    private final Deque<EventView> events = new ConcurrentLinkedDeque<>();

    @Override
    public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
        CircuitBreaker cb = entryAddedEvent.getAddedEntry();
        log.info("熔断器 [{}] 已创建", cb.getName());
        // 【坑】onEvent() 返回 void，不能和 onStateTransition() 链式调用，要分开注册
        cb.getEventPublisher().onEvent(this::record);
        cb.getEventPublisher().onStateTransition(e -> log.warn("【告警】熔断器 [{}] 状态切换：{}",
                e.getCircuitBreakerName(), e.getStateTransition()));
    }

    @Override
    public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
    }

    @Override
    public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
    }

    private void record(CircuitBreakerEvent e) {
        String detail = e instanceof CircuitBreakerOnStateTransitionEvent t
                ? t.getStateTransition().toString()
                : e.toString();
        events.addFirst(new EventView(e.getCreationTime(), e.getCircuitBreakerName(), e.getEventType().name(), detail));
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }

    public List<EventView> recent() {
        return new ArrayList<>(events);
    }
}
