package com.kai.learn.cbdemo.l3.mini;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.kai.learn.cbdemo.common.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【是什么】对照测试：用同一串随机的「调用结果 + 时间流逝」同时驱动手写版和 Resilience4j，
 * 每一步都断言两者的状态相同。
 *
 * 【为什么】手写简化版最怕「自以为理解了」。对照测试能精确地暴露理解偏差，比如：
 * 等待时间到了是不是自动切换为 HALF_OPEN？HALF_OPEN 是等所有探测结果都回来才判断，还是每次都判断？
 *
 * 【动手】把手写版中 HALF_OPEN 的判断改成「收到第一个失败就打开」，看看哪个随机种子会让测试失败，
 * 再对照 Resilience4j 的 {@code HalfOpenState#onError} 源码。
 */
class CompareWithResilience4jTest {

    @ParameterizedTest(name = "随机种子 {0}")
    @ValueSource(longs = {1, 2, 3, 42, 2026})
    void 同样的输入下状态序列完全一致(long seed) {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        MiniCircuitBreaker mini = new MiniCircuitBreaker("mini",
                new MiniCircuitBreakerConfig(10, 5, 50, Duration.ofSeconds(5), 3), clock);

        CircuitBreaker r4j = CircuitBreaker.of("r4j", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(3)
                .clock(clock)
                .build());

        Random random = new Random(seed);
        // 故障率在「健康」与「故障」两个阶段之间切换，让状态机走遍所有分支
        for (int step = 0; step < 2_000; step++) {
            double downstreamFailureRate = (step / 100) % 2 == 0 ? 0.1 : 0.8;
            boolean fails = random.nextDouble() < downstreamFailureRate;
            clock.advance(Duration.ofMillis(random.nextInt(800)));

            boolean miniPermitted = mini.tryAcquirePermission();
            boolean r4jPermitted = r4j.tryAcquirePermission();
            assertThat(miniPermitted).as("第 %d 步是否放行", step).isEqualTo(r4jPermitted);

            if (miniPermitted) {
                if (fails) {
                    mini.onError();
                    r4j.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException());
                } else {
                    mini.onSuccess();
                    r4j.onSuccess(1, TimeUnit.MILLISECONDS);
                }
            }
            assertThat(mini.getState().name()).as("第 %d 步之后的状态", step).isEqualTo(r4j.getState().name());
        }
    }
}
