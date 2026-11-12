package com.kai.learn.cbdemo.l3.window;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.kai.learn.cbdemo.common.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 对照测试：手写的时间窗口与 Resilience4j 的 TIME_BASED 窗口，在相同输入下失败率是否一致。
 *
 * 【原理】为了只比较「窗口」而不受状态切换干扰，把 Resilience4j 熔断器切换到 METRICS_ONLY 状态：
 * 只统计、永远不会打开。这个状态在生产环境中也有用，比如新接入熔断时先「只观察不拦截」，见 L4-04。
 */
class MiniTimeBasedWindowTest {

    @Test
    void 与Resilience4j的时间窗口失败率一致() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MiniTimeBasedWindow mini = new MiniTimeBasedWindow(5, clock);
        CircuitBreaker r4j = CircuitBreaker.of("r4j", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(1)
                .clock(clock)
                .build());
        r4j.transitionToMetricsOnlyState();

        Random random = new Random(7);
        for (int step = 0; step < 3_000; step++) {
            // 间隔 0~1.5 秒，有时一秒内多次调用，有时跳过整秒
            clock.advance(Duration.ofMillis(random.nextInt(1_500)));
            boolean failed = random.nextInt(100) < 30;
            mini.record(failed);
            if (failed) {
                r4j.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException());
            } else {
                r4j.onSuccess(1, TimeUnit.MILLISECONDS);
            }
            assertThat(mini.failureRate(1)).as("第 %d 步", step)
                    .isCloseTo(r4j.getMetrics().getFailureRate(), within(0.01f));
        }
    }
}
