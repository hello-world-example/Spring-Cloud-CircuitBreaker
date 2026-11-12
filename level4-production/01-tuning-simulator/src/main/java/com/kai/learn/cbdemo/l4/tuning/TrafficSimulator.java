package com.kai.learn.cbdemo.l4.tuning;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;

import com.kai.learn.cbdemo.common.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * 【是什么】熔断参数的「离线模拟器」：用虚拟时钟在几毫秒内模拟几分钟的流量，评估一组参数的两个核心指标：
 * <ul>
 *   <li><b>误熔断次数</b>：下游其实是健康的（只有零星的失败或短暂的抖动），熔断器却打开了。代价是正常请求被拒绝</li>
 *   <li><b>发现延迟</b>：下游真的出故障后，过了多久熔断器才打开。代价是这段时间内的请求都在等待超时、占用线程</li>
 * </ul>
 *
 * 【为什么】这两个指标此消彼长：窗口越小、阈值越低，发现得越快，但误熔断也越多。
 * 调参就是在两者之间找平衡点，而这个平衡点取决于接口的流量和失败特征，所以要用接近真实的流量模型来评估，
 * 不能凭感觉拍脑袋。
 *
 * 【生产】更好的做法是用线上的真实调用日志（时间戳 + 成功/失败）来回放，替代这里的随机流量模型。
 */
public class TrafficSimulator {

    public record Result(String name, int falseOpens, long detectionDelayMs, long rejectedWhileHealthy) {
    }

    /**
     * @param name           参数组名称
     * @param config         熔断配置（会被注入虚拟时钟）
     * @param qps            每秒调用次数
     * @param healthySeconds 健康阶段的时长
     * @param failureRateAt  健康阶段每一秒的下游失败率（用来模拟背景噪声与短暂抖动）
     * @param outageFailure  故障阶段的下游失败率
     * @param seed           随机种子，保证结果可复现
     */
    public static Result simulate(String name, CircuitBreakerConfig config, int qps, int healthySeconds,
                                  DoubleUnaryOperator failureRateAt, double outageFailure, long seed) {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CircuitBreaker cb = CircuitBreaker.of(name, CircuitBreakerConfig.from(config).clock(clock).build());
        int[] opens = {0};
        cb.getEventPublisher().onStateTransition(e -> {
            if (e.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                opens[0]++;
            }
        });

        Random random = new Random(seed);
        long stepMs = 1000L / qps;
        long healthyMs = healthySeconds * 1000L;
        long rejectedWhileHealthy = 0;
        int falseOpens = 0;
        long detectionDelay = -1;

        // 健康阶段 + 最多 60 秒的故障阶段
        for (long t = 0; t < healthyMs + 60_000; t += stepMs) {
            boolean healthy = t < healthyMs;
            double p = healthy ? failureRateAt.applyAsDouble(t / 1000.0) : outageFailure;
            if (cb.tryAcquirePermission()) {
                if (random.nextDouble() < p) {
                    cb.onError(10, TimeUnit.MILLISECONDS, new IllegalStateException());
                } else {
                    cb.onSuccess(10, TimeUnit.MILLISECONDS);
                }
            } else if (healthy) {
                rejectedWhileHealthy++;
            }
            if (healthy) {
                falseOpens = opens[0];
            } else if (detectionDelay < 0 && cb.getState() == CircuitBreaker.State.OPEN) {
                detectionDelay = t - healthyMs;
                break;
            }
            clock.advance(Duration.ofMillis(stepMs));
        }
        return new Result(name, falseOpens, detectionDelay, rejectedWhileHealthy);
    }
}
