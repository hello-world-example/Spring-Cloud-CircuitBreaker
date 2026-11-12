package com.kai.learn.cbdemo.l3.source;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【是什么】证明：默认配置下，run() 中的业务代码运行在另一个线程中，ThreadLocal 和 MDC 都会丢失。
 *
 * 【原理】源码 {@code Resilience4JCircuitBreakerFactory}：
 * <pre>
 *   private ExecutorService executorService = Executors.newCachedThreadPool();   // ← 默认就有一个线程池
 * </pre>
 * {@code Resilience4JCircuitBreaker#run} 中（没有舱壁、executorService 不为空的分支）：
 * <pre>
 *   Supplier&lt;Future&lt;T&gt;&gt; futureSupplier = () -&gt; executorService.submit(toRun::get);   // ← 线程切换
 *   Callable&lt;T&gt; restrictedCall = TimeLimiter.decorateFutureSupplier(tl, futureSupplier);  // ← Future.get(timeout)
 *   Callable&lt;T&gt; callable = CircuitBreaker.decorateCallable(cb, restrictedCall);
 * </pre>
 * 之所以要切换线程，是因为 TimeLimiter 需要通过 {@code Future.get(timeout)} 实现超时。
 *
 * 【坑】线上的典型现象：日志中熔断保护的那一段<b>没有 traceId</b>；下游收不到用户信息；
 * {@code @Transactional} 的事务在 run() 中失效（事务也绑定在线程上）。
 */
@SpringBootTest(classes = FactorySourceApplication.class)
class ThreadSwitchTest {

    @Autowired
    CircuitBreakerFactory<?, ?> factory;

    @AfterEach
    void cleanUp() {
        MDC.clear();
        UserContext.clear();
    }

    @Test
    void 默认情况下业务代码运行在另一个线程中且上下文丢失() {
        MDC.put("traceId", "trace-123");
        UserContext.set("kai");
        String callerThread = Thread.currentThread().getName();

        Map<String, String> seen = factory.create("plain").run(() -> Map.of(
                "thread", Thread.currentThread().getName(),
                "traceId", String.valueOf(MDC.get("traceId")),
                "user", String.valueOf(UserContext.get())));

        System.out.printf("%n调用方线程：%s%nrun() 中看到的：%s%n", callerThread, seen);
        assertThat(seen.get("thread")).isNotEqualTo(callerThread).startsWith("pool-");
        assertThat(seen.get("traceId")).isEqualTo("null");
        assertThat(seen.get("user")).isEqualTo("null");
    }
}
