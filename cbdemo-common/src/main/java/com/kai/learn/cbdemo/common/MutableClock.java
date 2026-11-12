package com.kai.learn.cbdemo.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可以手动拨动的时钟，用于 L3 的测试：「等待 5 秒」不需要真的 sleep，调用 {@link #advance(Duration)} 即可。
 *
 * <p>【原理】Resilience4j 的 CircuitBreakerConfig 支持注入 {@link Clock}（{@code .clock(clock)}），
 * OPEN 状态的等待时间和时间窗口都基于它计算。依赖注入时钟而不是直接调用 System.currentTimeMillis()，
 * 是让时间相关的逻辑可以被测试的常见手法。
 */
public class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public synchronized void advance(Duration d) {
        now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public synchronized Instant instant() {
        return now;
    }
}
