package com.kai.learn.cbdemo.l3.order;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【是什么】同样的 Retry（最多 3 次）+ CircuitBreaker（窗口 10、至少 5 次、50%），只是嵌套顺序不同，
 * 在「下游持续故障」时的表现有多大差别。
 *
 * 【原理】{@code Decorators.ofSupplier(x).withA().withB()}：<b>先 with 的在内层</b>，结果是 B(A(x))。
 * <pre>
 *   顺序一：Retry 在外层  Retry( CircuitBreaker( 调用 ) )
 *     一次请求 = 最多 3 次经过熔断器 → 熔断器看到的调用次数被放大 3 倍
 *     熔断打开后，Retry 还会去重试 CallNotPermittedException（除非把它排除掉）
 *
 *   顺序二：Retry 在内层  CircuitBreaker( Retry( 调用 ) )
 *     一次请求 = 熔断器只看到 1 次调用（重试 3 次都失败才算 1 次失败）
 *     对下游的压力 = 请求数 × 3，直到熔断打开
 * </pre>
 * Resilience4j 注解方式（resilience4j-spring-boot）的默认顺序是：
 * {@code Retry( CircuitBreaker( RateLimiter( TimeLimiter( Bulkhead( 调用 ) ) ) ) )}，也就是 Retry 在最外层。
 *
 * 【坑】Retry 在外层时，务必把 CallNotPermittedException 排除在重试之外：熔断已经打开了，再重试只是白白等待。
 */
class DecoratorOrderTest {

    record Outcome(String name, int downstreamCalls, int requestsBeforeOpen, int cbRecordedCalls) {
    }

    @Test
    void 对比两种顺序() {
        Outcome retryOutside = run("Retry 在外层 Retry(CB(x))", true);
        Outcome retryInside = run("Retry 在内层 CB(Retry(x))", false);

        System.out.println();
        System.out.println("下游持续故障，发起 20 个请求：");
        System.out.printf("%-26s 下游实际被调用 %3d 次，第 %2d 个请求时熔断打开，熔断器记录了 %d 次调用%n",
                retryOutside.name(), retryOutside.downstreamCalls(), retryOutside.requestsBeforeOpen(),
                retryOutside.cbRecordedCalls());
        System.out.printf("%-26s 下游实际被调用 %3d 次，第 %2d 个请求时熔断打开，熔断器记录了 %d 次调用%n",
                retryInside.name(), retryInside.downstreamCalls(), retryInside.requestsBeforeOpen(),
                retryInside.cbRecordedCalls());

        // Retry 在外层：熔断器很快就看到 5 次失败（2 个请求就够了），更早打开
        assertThat(retryOutside.requestsBeforeOpen()).isLessThan(retryInside.requestsBeforeOpen());
        // Retry 在内层：熔断打开之前，每个请求都让下游承受 3 次调用
        assertThat(retryInside.downstreamCalls()).isEqualTo(retryInside.requestsBeforeOpen() * 3);
    }

    private Outcome run(String name, boolean retryOutside) {
        CircuitBreaker cb = CircuitBreaker.of("cb", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        Retry retry = Retry.of("retry", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                // 熔断打开后不要重试
                .ignoreExceptions(CallNotPermittedException.class)
                .build());

        AtomicInteger downstreamCalls = new AtomicInteger();
        Supplier<String> downstream = () -> {
            downstreamCalls.incrementAndGet();
            throw new IllegalStateException("下游 500");
        };

        Supplier<String> decorated = retryOutside
                ? Decorators.ofSupplier(downstream).withCircuitBreaker(cb).withRetry(retry).decorate()
                : Decorators.ofSupplier(downstream).withRetry(retry).withCircuitBreaker(cb).decorate();

        int openedAt = -1;
        for (int request = 1; request <= 20; request++) {
            try {
                decorated.get();
            } catch (RuntimeException ignored) {
                // 预期中的失败
            }
            if (openedAt < 0 && cb.getState() == CircuitBreaker.State.OPEN) {
                openedAt = request;
            }
        }
        return new Outcome(name, downstreamCalls.get(), openedAt, cb.getMetrics().getNumberOfBufferedCalls());
    }
}
