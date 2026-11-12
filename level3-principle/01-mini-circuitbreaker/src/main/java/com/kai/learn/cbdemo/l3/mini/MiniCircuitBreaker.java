package com.kai.learn.cbdemo.l3.mini;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 【是什么】手写的简化版熔断器，约 150 行，行为与 Resilience4j 的 COUNT_BASED 熔断器一致
 * （由 {@code CompareWithResilience4jTest} 验证）。
 *
 * 【原理】状态机：
 * <pre>
 *   CLOSED ──失败率 ≥ 阈值──▶ OPEN ──等待时间到 + 有新调用──▶ HALF_OPEN
 *     ▲                         ▲                                │
 *     │                         └──── 探测请求的失败率 ≥ 阈值 ───┤
 *     └──────────────────────── 探测请求的失败率 < 阈值 ─────────┘
 * </pre>
 * 每个状态各自持有一份数据：
 * <ul>
 *   <li>CLOSED：一个大小为 slidingWindowSize 的滑动窗口</li>
 *   <li>OPEN：「什么时候可以再试」的时间点</li>
 *   <li>HALF_OPEN：剩余的探测名额 + 一个大小为 permittedCallsInHalfOpen 的新窗口</li>
 * </ul>
 * 状态切换时，旧状态连同它的数据一起被丢弃。所以 OPEN → HALF_OPEN → CLOSED 之后，失败率是从零开始统计的。
 *
 * 【对比】Resilience4j 的实现（{@code CircuitBreakerStateMachine}）：
 * <ul>
 *   <li>每个状态是一个对象（ClosedState / OpenState / HalfOpenState ……），用 {@code AtomicReference} 保存当前状态，
 *       切换时用 CAS 替换，是无锁的；本实现为了易读，直接使用 synchronized</li>
 *   <li>多了 DISABLED、FORCED_OPEN、METRICS_ONLY 三个「人工干预」状态（见 L4-04）</li>
 *   <li>多了慢调用统计、按时间统计的窗口、事件发布等功能</li>
 * </ul>
 */
public class MiniCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** 状态 + 这个状态私有的数据。不可变，切换状态时整体替换 */
    private sealed interface StateData permits Closed, Open, HalfOpen {
        State state();
    }

    private record Closed(CountBasedSlidingWindow window) implements StateData {
        public State state() {
            return State.CLOSED;
        }
    }

    private record Open(Instant retryAfter) implements StateData {
        public State state() {
            return State.OPEN;
        }
    }

    private static final class HalfOpen implements StateData {
        private final CountBasedSlidingWindow window;
        private int remainingPermits;

        HalfOpen(int permits) {
            this.window = new CountBasedSlidingWindow(permits);
            this.remainingPermits = permits;
        }

        public State state() {
            return State.HALF_OPEN;
        }
    }

    private final String name;
    private final MiniCircuitBreakerConfig config;
    private final Clock clock;
    private final List<Consumer<String>> transitionListeners = new CopyOnWriteArrayList<>();
    private StateData current;
    private long notPermittedCalls;

    public MiniCircuitBreaker(String name, MiniCircuitBreakerConfig config, Clock clock) {
        this.name = name;
        this.config = config;
        this.clock = clock;
        this.current = new Closed(new CountBasedSlidingWindow(config.slidingWindowSize()));
    }

    /**
     * 最常用的入口：包装一次调用。
     */
    public <T> T executeSupplier(Supplier<T> supplier) {
        if (!tryAcquirePermission()) {
            throw new MiniCallNotPermittedException(name);
        }
        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onError();
            throw e;
        }
    }

    /**
     * 第 1 步：调用前申请许可。
     */
    public synchronized boolean tryAcquirePermission() {
        return switch (current) {
            case Closed c -> true;
            case Open o -> {
                // 【原理】等待时间到了也不会自己切换为 HALF_OPEN，而是由「这一次调用」触发切换。
                // 这就是 Resilience4j 默认 automaticTransitionFromOpenToHalfOpenEnabled=false 的行为
                if (!clock.instant().isBefore(o.retryAfter())) {
                    transitionTo(new HalfOpen(config.permittedCallsInHalfOpen()));
                    yield tryAcquirePermission();
                }
                notPermittedCalls++;
                yield false;
            }
            case HalfOpen h -> {
                if (h.remainingPermits > 0) {
                    h.remainingPermits--;
                    yield true;
                }
                // 探测名额用完了，但探测结果还没全部回来：其余请求继续被拒绝
                notPermittedCalls++;
                yield false;
            }
        };
    }

    /** 第 2 步（成功）：记录结果 */
    public synchronized void onSuccess() {
        record(false);
    }

    /** 第 2 步（失败）：记录结果 */
    public synchronized void onError() {
        record(true);
    }

    private void record(boolean failed) {
        switch (current) {
            case Closed c -> {
                float rate = c.window().record(failed).failureRate(config.minimumNumberOfCalls());
                if (rate >= config.failureRateThreshold()) {
                    open();
                }
            }
            case HalfOpen h -> {
                // HALF_OPEN 状态下，等所有探测请求的结果都回来（窗口填满）才做判断
                float rate = h.window.record(failed).failureRate(config.permittedCallsInHalfOpen());
                if (rate >= config.failureRateThreshold()) {
                    open();
                } else if (rate >= 0) {
                    transitionTo(new Closed(new CountBasedSlidingWindow(config.slidingWindowSize())));
                }
            }
            // 【坑】OPEN 状态下也可能收到结果：在打开「之前」就已经放行的慢调用，打开之后才返回。直接忽略即可
            case Open o -> {
            }
        }
    }

    private void open() {
        transitionTo(new Open(clock.instant().plus(config.waitDurationInOpenState())));
    }

    private void transitionTo(StateData next) {
        String transition = current.state() + " -> " + next.state();
        current = next;
        transitionListeners.forEach(l -> l.accept(transition));
    }

    public synchronized State getState() {
        return current.state();
    }

    public synchronized long getNotPermittedCalls() {
        return notPermittedCalls;
    }

    /** 当前窗口中的失败率；样本不足时为 -1 */
    public synchronized float getFailureRate() {
        return switch (current) {
            case Closed c -> c.window().snapshot().failureRate(config.minimumNumberOfCalls());
            case HalfOpen h -> h.window.snapshot().failureRate(config.permittedCallsInHalfOpen());
            case Open o -> -1;
        };
    }

    public void onStateTransition(Consumer<String> listener) {
        transitionListeners.add(listener);
    }

    public String getName() {
        return name;
    }
}
