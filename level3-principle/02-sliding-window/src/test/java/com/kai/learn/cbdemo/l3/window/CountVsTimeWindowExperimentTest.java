package com.kai.learn.cbdemo.l3.window;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.kai.learn.cbdemo.common.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【是什么】两个实验，说明「按次数」和「按时间」两种窗口分别在什么场景下会出问题。运行后看控制台输出的表格。
 *
 * 【原理】
 * <ul>
 *   <li>COUNT_BASED 窗口覆盖的<b>时间跨度</b>随流量变化：流量低时一个窗口可能跨越几十分钟（数据陈旧），
 *       流量高时只覆盖零点几秒（对瞬时抖动过于敏感）</li>
 *   <li>TIME_BASED 窗口覆盖的<b>样本数</b>随流量变化：流量低时样本太少（由 minimumNumberOfCalls 兜底），
 *       流量高时样本充足、结果稳定</li>
 * </ul>
 * 结论：流量稳定且较高的接口优先选择 TIME_BASED；流量很低的接口两种都不理想，详见 docs/02-sliding-window.md。
 */
class CountVsTimeWindowExperimentTest {

    @Test
    void 实验一_低流量时计数窗口会保留陈旧的失败() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CircuitBreaker count = metricsOnly("count", CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, 10, clock);
        CircuitBreaker time = metricsOnly("time", CircuitBreakerConfig.SlidingWindowType.TIME_BASED, 60, clock);

        System.out.println("\n实验一：每分钟 1 次调用；第 0~4 分钟下游故障，之后恢复");
        System.out.println("分钟  结果  COUNT(最近10次)  TIME(最近60秒)");
        float[] countRate = new float[15];
        float[] timeRate = new float[15];
        for (int minute = 0; minute < 15; minute++) {
            boolean failed = minute < 5;
            record(count, failed);
            record(time, failed);
            countRate[minute] = count.getMetrics().getFailureRate();
            timeRate[minute] = time.getMetrics().getFailureRate();
            System.out.printf("%4d  %s   %8.1f%%       %8.1f%%%n", minute, failed ? "失败" : "成功",
                    countRate[minute], timeRate[minute]);
            clock.advance(Duration.ofMinutes(1));
        }
        // 第 9 分钟时，下游已经恢复了 5 分钟：
        // 计数窗口中仍然有 5 次失败，失败率 50%（如果阈值是 50%，此时仍然会被判定为故障）
        // 时间窗口里只有最近 60 秒内的 1 次成功调用，失败率 0%
        assertThat(countRate[9]).isEqualTo(50f);
        assertThat(timeRate[9]).isEqualTo(0f);
        // 计数窗口直到第 14 分钟才把最后一次失败挤出
        assertThat(countRate[14]).isEqualTo(0f);
    }

    @Test
    void 实验二_高流量时计数窗口对瞬时抖动过于敏感() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CircuitBreaker count = metricsOnly("count", CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, 100, clock);
        CircuitBreaker time = metricsOnly("time", CircuitBreakerConfig.SlidingWindowType.TIME_BASED, 10, clock);

        // 200 QPS，持续 10 秒；第 9.5 秒开始出现 0.5 秒的抖动（这 100 次调用全部失败，比如一次 GC 停顿）
        for (int i = 0; i < 2_000; i++) {
            boolean failed = i >= 1_900;
            record(count, failed);
            record(time, failed);
            clock.advance(Duration.ofMillis(5));
        }
        System.out.println("\n实验二：200 QPS，最后 0.5 秒（100 次调用）全部失败");
        System.out.printf("COUNT(最近100次) 失败率 = %.1f%%   ← 窗口只覆盖了 0.5 秒，全部是抖动期间的调用%n",
                count.getMetrics().getFailureRate());
        System.out.printf("TIME(最近10秒)   失败率 = %.1f%%     ← 窗口覆盖 2000 次调用，抖动被稀释%n",
                time.getMetrics().getFailureRate());

        assertThat(count.getMetrics().getFailureRate()).isEqualTo(100f);
        assertThat(time.getMetrics().getFailureRate()).isLessThan(10f);
    }

    private static CircuitBreaker metricsOnly(String name, CircuitBreakerConfig.SlidingWindowType type, int size,
                                              MutableClock clock) {
        CircuitBreaker cb = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowType(type)
                .slidingWindowSize(size)
                .minimumNumberOfCalls(1)
                .clock(clock)
                .build());
        // 只统计、不切换状态，便于观察窗口本身
        cb.transitionToMetricsOnlyState();
        return cb;
    }

    private static void record(CircuitBreaker cb, boolean failed) {
        if (failed) {
            cb.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException());
        } else {
            cb.onSuccess(1, TimeUnit.MILLISECONDS);
        }
    }
}
