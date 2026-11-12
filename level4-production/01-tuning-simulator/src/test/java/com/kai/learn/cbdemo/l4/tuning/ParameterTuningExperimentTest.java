package com.kai.learn.cbdemo.l4.tuning;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;
import static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.TIME_BASED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【是什么】用同一个流量模型评估 5 组参数，输出对比表。
 *
 * 流量模型：50 QPS；健康阶段 5 分钟，背景失败率 2%，每 60 秒出现一次持续 1 秒、失败率 60% 的抖动
 * （比如下游 GC、网络瞬断）；然后下游故障，失败率 80%。
 *
 * 【动手】把 QPS 改成 2（低流量接口），再看看哪组参数还能用。
 */
class ParameterTuningExperimentTest {

    private static final int QPS = 50;

    /** 背景噪声 2%，每分钟的第 30 秒有 1 秒的抖动 */
    private static final DoubleUnaryOperator HEALTHY_WITH_JITTER = second -> (second % 60 >= 30 && second % 60 < 31) ? 0.6 : 0.02;

    @Test
    void 对比不同参数组的误熔断与发现延迟() {
        List<TrafficSimulator.Result> results = new ArrayList<>();
        results.add(run("A 计数10 /最少5  /50%", config(COUNT_BASED, 10, 5, 50)));
        results.add(run("B 计数100/最少50 /50%", config(COUNT_BASED, 100, 50, 50)));
        results.add(run("C 时间5s /最少20 /50%", config(TIME_BASED, 5, 20, 50)));
        results.add(run("D 时间10s/最少50 /50%", config(TIME_BASED, 10, 50, 50)));
        results.add(run("E 时间10s/最少50 /20%", config(TIME_BASED, 10, 50, 20)));

        System.out.println();
        System.out.println("50 QPS，健康 5 分钟（背景失败 2%，每分钟一次 1 秒的 60% 抖动），之后故障（失败 80%）");
        System.out.println("参数组                    误熔断次数  健康期被拒绝的请求  发现故障用时");
        for (TrafficSimulator.Result r : results) {
            System.out.printf("%-24s %6d %16d %12dms%n",
                    r.name(), r.falseOpens(), r.rejectedWhileHealthy(), r.detectionDelayMs());
        }

        // 所有参数组最终都能发现故障
        results.forEach(r -> assertThat(r.detectionDelayMs()).as(r.name()).isGreaterThanOrEqualTo(0));
    }

    private static TrafficSimulator.Result run(String name, CircuitBreakerConfig config) {
        return TrafficSimulator.simulate(name, config, QPS, 300, HEALTHY_WITH_JITTER, 0.8, 2026);
    }

    private static CircuitBreakerConfig config(CircuitBreakerConfig.SlidingWindowType type, int size, int minCalls,
                                               float threshold) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(type)
                .slidingWindowSize(size)
                .minimumNumberOfCalls(minCalls)
                .failureRateThreshold(threshold)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(5)
                .build();
    }
}
