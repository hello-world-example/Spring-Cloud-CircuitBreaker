package com.kai.learn.cbdemo.l3.mini;

import java.time.Duration;

/**
 * 手写熔断器的配置，只保留了最核心的 5 个参数，含义与 Resilience4j 同名参数完全一致。
 *
 * @param slidingWindowSize            滑动窗口大小（最近 N 次调用）
 * @param minimumNumberOfCalls         窗口中至少有这么多次调用才计算失败率
 * @param failureRateThreshold         失败率阈值（百分比），≥ 阈值时打开
 * @param waitDurationInOpenState      OPEN 状态持续的时间
 * @param permittedCallsInHalfOpen     HALF_OPEN 状态放行的探测请求数
 */
public record MiniCircuitBreakerConfig(int slidingWindowSize,
                                       int minimumNumberOfCalls,
                                       float failureRateThreshold,
                                       Duration waitDurationInOpenState,
                                       int permittedCallsInHalfOpen) {

    public MiniCircuitBreakerConfig {
        if (slidingWindowSize < 1 || permittedCallsInHalfOpen < 1) {
            throw new IllegalArgumentException("窗口大小与探测请求数必须 ≥ 1");
        }
        // 【原理】与 Resilience4j 相同：最少调用数不能超过窗口大小，否则永远达不到
        minimumNumberOfCalls = Math.min(minimumNumberOfCalls, slidingWindowSize);
    }
}
