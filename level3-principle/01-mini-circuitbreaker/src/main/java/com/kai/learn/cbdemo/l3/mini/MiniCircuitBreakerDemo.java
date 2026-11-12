package com.kai.learn.cbdemo.l3.mini;

import java.time.Duration;
import java.time.Instant;

import com.kai.learn.cbdemo.common.MutableClock;

/**
 * 直接运行 main 方法，打印一次完整的生命周期：CLOSED → OPEN → HALF_OPEN → CLOSED。
 */
public class MiniCircuitBreakerDemo {

    public static void main(String[] args) {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MiniCircuitBreaker cb = new MiniCircuitBreaker("demo",
                new MiniCircuitBreakerConfig(10, 5, 50, Duration.ofSeconds(5), 3), clock);
        cb.onStateTransition(t -> System.out.println("    >>> 状态切换：" + t));

        System.out.println("== 阶段 1：下游故障，连续失败");
        for (int i = 1; i <= 8; i++) {
            call(cb, i, true);
        }

        System.out.println("== 阶段 2：等待 5 秒（拨动时钟），下游恢复");
        clock.advance(Duration.ofSeconds(5));
        for (int i = 9; i <= 13; i++) {
            call(cb, i, false);
        }
    }

    private static void call(MiniCircuitBreaker cb, int seq, boolean downstreamFails) {
        String result;
        try {
            result = cb.executeSupplier(() -> {
                if (downstreamFails) {
                    throw new IllegalStateException("下游 500");
                }
                return "ok";
            });
        } catch (MiniCallNotPermittedException e) {
            result = "被拒绝（快速失败）";
        } catch (IllegalStateException e) {
            result = "失败：" + e.getMessage();
        }
        System.out.printf("#%-2d %-14s state=%-9s failureRate=%s%n", seq, result, cb.getState(), cb.getFailureRate());
    }
}
